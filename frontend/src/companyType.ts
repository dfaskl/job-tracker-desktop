export type CompanyClassification = { company: string; industry?: string; industryStatus?: string }

export function companyKey(value: unknown) {
  return String(value || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN')
}

export function companyType(entry?: CompanyClassification) {
  return entry?.industry?.trim() || (entry?.industryStatus === 'pending' ? 'AI识别中' : entry?.industryStatus === 'failed' ? '识别失败' : '未分类')
}
