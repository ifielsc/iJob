/* eslint-disable no-control-regex -- Explicitly reject Java ISO control characters. */
// Shared account validation matches Java's UTF-16 length and UTF-8 byte limit.
export const validUsername = (value: string) => /^[A-Za-z0-9][A-Za-z0-9_.@-]{2,63}$/.test(value)
export const validDisplayName = (value: string) =>
  value.length <= 128 && !/[\u0000-\u001f\u007f-\u009f]/u.test(value)
export const validPassword = (value: string) =>
  value.length >= 12 &&
  value.length <= 72 &&
  new TextEncoder().encode(value).length <= 72 &&
  value.trim().length > 0 &&
  !/[\u0000-\u001f\u007f-\u009f]/u.test(value)
