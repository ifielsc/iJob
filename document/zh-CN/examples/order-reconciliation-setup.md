# 订单对账案例准备

[English](../../en/examples/order-reconciliation-setup.md) · [返回订单对账教程](order-reconciliation.md)

本页仅准备合成业务数据与本地接收端。先完成主教程的前置条件，使用专属业务库和文件目录，不覆盖已有订单案例或其他教程数据。完成后返回主教程第 3 步创建资源。

## 初始化业务数据

在**订单来源库**执行：

```sql
CREATE TABLE source_orders (
  biz_date varchar(10) NOT NULL,
  order_id bigint NOT NULL,
  amount numeric(12,2),
  customer_region varchar(30) NOT NULL,
  PRIMARY KEY (biz_date, order_id)
);

INSERT INTO source_orders (biz_date, order_id, amount, customer_region)
SELECT d.biz_date, n,
       CASE WHEN d.biz_date = '2026-10-30' AND n = 1
            THEN NULL ELSE n * 100 END,
       CASE n % 3 WHEN 0 THEN 'North'
                  WHEN 1 THEN 'East' ELSE 'South' END
FROM (VALUES ('2026-10-28'), ('2026-10-29'), ('2026-10-30')) AS d(biz_date)
CROSS JOIN generate_series(1,12) AS n;
```

在**订单对账库**执行：

```sql
CREATE TABLE orders (
  biz_date varchar(10) NOT NULL,
  order_id bigint NOT NULL,
  amount numeric(12,2),
  customer_region varchar(30) NOT NULL,
  PRIMARY KEY (biz_date, order_id)
);

CREATE TABLE bank_receipts (
  biz_date varchar(10) NOT NULL,
  order_id bigint NOT NULL,
  amount numeric(12,2) NOT NULL,
  PRIMARY KEY (biz_date, order_id)
);

INSERT INTO bank_receipts (biz_date, order_id, amount)
SELECT d.biz_date, n,
       CASE WHEN d.biz_date = '2026-10-29' AND n = 1
            THEN 0 ELSE n * 100 END
FROM (VALUES ('2026-10-28'), ('2026-10-29'), ('2026-10-30')) AS d(biz_date)
CROSS JOIN generate_series(1,12) AS n;
```

## 本地回执接收端代码

保存为 `receipt_receiver.py`。只读取传入交付目录中的三个样例日期，回执记录写到你明确指定的文件；仅依赖 Python 标准库。`/health` 返回状态和记录数；`/orders` 为后续接口扩展提供固定的合成批次，不读取或修改数据库。

```python
from argparse import ArgumentParser
from csv import DictReader
from decimal import Decimal, InvalidOperation
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
import json

DATES = {"2026-10-28", "2026-10-29", "2026-10-30"}

parser = ArgumentParser()
parser.add_argument("--port", type=int, required=True)
parser.add_argument("--delivered-root", type=Path, required=True)
parser.add_argument("--records", type=Path, required=True)
args = parser.parse_args()
if not 1 <= args.port <= 65535:
    parser.error("port must be between 1 and 65535")
root = args.delivered_root.resolve(strict=True)
records = args.records.resolve()
if not root.is_dir() or not records.parent.is_dir():
    parser.error("create the delivery and record directories first")

class Receiver(BaseHTTPRequestHandler):
    def log_message(self, *values):
        pass

    def reply(self, status, value):
        data = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        request = urlsplit(self.path)
        if request.path == "/health":
            count = 0
            if records.is_file():
                with records.open(encoding="utf-8") as stream:
                    count = sum(1 for line in stream if line.strip())
            return self.reply(200, {"status": "ok", "receiptCount": count})
        if request.path == "/orders":
            date = parse_qs(request.query).get("bizDate", ["2026-10-28"])[0]
            if date not in DATES:
                return self.reply(400, {"ok": False, "reason": "Invalid sample date"})
            return self.reply(200, {
                "ok": True, "bizDate": date, "rows": 12,
                "totalAmount": 7800, "batchId": "ORD-" + date,
                "orders": [{"orderId": n, "amount": n * 100}
                           for n in range(1, 13)],
            })
        self.reply(404, {"ok": False})

    def do_POST(self):
        if self.path != "/receipts":
            return self.reply(404, {"ok": False})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 65536:
                return self.reply(400, {"ok": False, "reason": "Invalid body size"})
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                return self.reply(400, {"ok": False, "reason": "Expected an object"})
            date = body.get("bizDate")
            if date not in DATES or body.get("fileName") != "orders.csv":
                return self.reply(400, {"ok": False, "reason": "Invalid sample input"})
            file = (root / date / "orders.csv").resolve()
            if not file.is_relative_to(root) or not file.is_file():
                return self.reply(409, {"ok": False, "reason": "File not delivered"})
            with file.open(encoding="utf-8-sig", newline="") as stream:
                rows = list(DictReader(stream))
            amount = sum((Decimal(row["amount"]) for row in rows), Decimal(0))
            root_id = str(body.get("rootInstanceId", ""))
            result = {
                "ok": len(rows) == 12 and amount == Decimal("7800"),
                "receiptId": "RCPT-" + date + "-" + root_id,
                "bizDate": date,
                "rootInstanceId": root_id,
                "rows": len(rows),
                "totalAmount": float(amount),
            }
            with records.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(result) + "\n")
            self.reply(200, result)
        except (ValueError, TypeError, KeyError, OSError, InvalidOperation):
            self.reply(400, {"ok": False, "reason": "Invalid request or CSV"})

HTTPServer(("127.0.0.1", args.port), Receiver).serve_forever()
```

样例成功响应如下；`receiptId` 与 `rootInstanceId` 以你的实际运行值为准：

```json
{
  "ok": true,
  "receiptId": "RCPT-2026-10-28-<root-instance-id>",
  "bizDate": "2026-10-28",
  "rootInstanceId": "<root-instance-id>",
  "rows": 12,
  "totalAmount": 7800
}
```


[返回主教程](order-reconciliation.md)
