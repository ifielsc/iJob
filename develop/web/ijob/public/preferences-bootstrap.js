try {
  const preference = JSON.parse(localStorage.getItem('ijob:preferences:v1:guest') || 'null')
  if (preference?.theme === 'dark') document.documentElement.dataset.theme = 'dark'
  if (preference?.locale === 'en-US') document.documentElement.lang = 'en-US'
} catch {
  // Storage may be unavailable. The default remains light / zh-CN.
}
