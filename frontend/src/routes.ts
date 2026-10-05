const supportedPages = new Set([
  'home', 'applications', 'calendar', 'mail', 'stats',
  'interview-summary', 'profile', 'settings', 'admin', 'guide'
])

export function readHashRoute(hash: string): string {
  return hash.replace(/^#\/?/, '') || 'home'
}

export function pageFromRoute(route: string): string {
  return route.split('?')[0]
}

export function isSupportedRoute(route: string): boolean {
  return supportedPages.has(pageFromRoute(route))
}

export function resolveRequestedRoute(hash: string, sessionRoute: string): string {
  const route = readHashRoute(hash)
  if (pageFromRoute(route) === 'login' && isSupportedRoute(sessionRoute)) return sessionRoute
  return isSupportedRoute(route) ? route : 'home'
}
