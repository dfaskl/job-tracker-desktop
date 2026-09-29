const KNOWN_EVENT_TYPES = new Set(['测评', '笔试', '面试', 'Offer', '未通过', '其他'])
const AUTOMATED_INTERVIEW = /(?:AI\s*(?:面试|初面|面)|数字人面试|智能面试|机器面试)/i
const INTERVIEW_NAME = /面试|[一二三四五六七八九]面|HR面?|电话面?/i

export function isFormalInterview(event: Record<string, unknown>) {
  const type = String(event.type || '').trim()
  if (KNOWN_EVENT_TYPES.has(type)) return type === '面试'
  const title = String(event.title || '').trim()
  if (AUTOMATED_INTERVIEW.test(`${type} ${title}`)) return false
  return INTERVIEW_NAME.test(`${type} ${title}`)
}