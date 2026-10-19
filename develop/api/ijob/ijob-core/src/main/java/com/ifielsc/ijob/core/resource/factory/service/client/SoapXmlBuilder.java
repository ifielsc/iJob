// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

import com.ifielsc.ijob.core.resource.factory.service.WebServiceConfig;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceRequest;

public class SoapXmlBuilder {
    private static final String XML_NAME = "[A-Za-z_][A-Za-z0-9_.-]*";

    public static String build(WebServiceConfig config, WebServiceRequest request) {
        requireXmlName(request.getOperation(), "operation");
        StringBuilder xml = new StringBuilder();
        xml.append("<soapenv:Envelope ")
                .append("xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
                .append("xmlns:ns=\"")
                .append(escape(config.getNamespace()))
                .append("\">");
        xml.append("<soapenv:Body>");
        xml.append("<ns:").append(request.getOperation()).append(">");

        if (request.getParameters() != null) {
            request.getParameters()
                    .forEach(
                            (k, v) -> {
                                requireXmlName(k, "parameter");
                                xml.append("<")
                                        .append(k)
                                        .append(">")
                                        .append(escape(String.valueOf(v)))
                                        .append("</")
                                        .append(k)
                                        .append(">");
                            });
        }

        xml.append("</ns:").append(request.getOperation()).append(">");
        xml.append("</soapenv:Body>");
        xml.append("</soapenv:Envelope>");
        return xml.toString();
    }

    private static void requireXmlName(String value, String field) {
        if (value == null || !value.matches(XML_NAME)) {
            throw new IllegalArgumentException("WebService " + field + " is invalid");
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
