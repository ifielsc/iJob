// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

export const fileProtocols = ['LOCAL', 'FTP', 'SFTP', 'S3_COMPATIBLE'] as const
export type FileProtocol = (typeof fileProtocols)[number]
