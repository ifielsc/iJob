package com.ifielsc.ijob.contract.monitor.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkMetric {
    private String name;

    private String displayName;

    /** MAC */
    private String mac;

    /** IPv4 */
    @Builder.Default private List<String> ipv4 = new ArrayList<>();

    /** IPv6 */
    @Builder.Default private List<String> ipv6 = new ArrayList<>();

    private Long receiveBytes;

    private Long sendBytes;

    /** Receive rate in bytes per second. */
    private Long receiveRate;

    /** Send rate in bytes per second. */
    private Long sendRate;
}
