export const fileProtocols = ['LOCAL', 'FTP', 'SFTP', 'S3_COMPATIBLE'] as const
export type FileProtocol = (typeof fileProtocols)[number]
