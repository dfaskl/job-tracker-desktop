import type ExcelJS from 'exceljs'
import type { BusinessData, JobApplication, JobEvent, User } from './jobTrackerStore'
import { shanghaiDateKey } from './shanghaiTime'

type ExportUser = Pick<User, 'email' | 'displayName'>
type TimelineNode = {
  order: number
  time: string
  timeMode: '时间点' | '时间段' | '当前状态'
  name: string
  state: string
  location: string
  notes: string
}

const COLORS = {
  title: '0F766E',
  titleText: 'FFFFFF',
  header: '1F4E78',
  headerText: 'FFFFFF',
  border: 'D7DEE8',
  stripe: 'F6F8FB',
  applied: 'DCEBFA',
  assessment: 'E9DDF8',
  test: 'FCE8C5',
  interview: 'D9F0EF',
  offer: 'DDF3E6',
  stopped: 'F8DDDC'
} as const

export function exportRawBusinessData(data: BusinessData, user: ExportUser) {
  const clean = structuredClone(data)
  if (clean.settings && typeof clean.settings === 'object') delete clean.settings.apiKey
  const payload = JSON.stringify(clean, null, 2)
  downloadBlob(new Blob([payload], { type: 'application/json;charset=utf-8' }), `${fileStem(user)}-原始数据-${shanghaiDateKey()}.json`)
}

export async function exportReadableBusinessData(data: BusinessData, user: ExportUser) {
  const { default: ExcelJS } = await import('exceljs')
  const workbook = new ExcelJS.Workbook()
  workbook.creator = '求职进度本'
  workbook.created = new Date()
  workbook.modified = new Date()
  workbook.subject = '求职投递记录与时间线'

  const applications = [...(data.applications || [])].sort(compareApplications)
  const events = data.events || []
  const records = workbook.addWorksheet('投递记录', {
    views: [{ state: 'frozen', ySplit: 4 }],
    properties: { tabColor: { argb: COLORS.title } }
  })
  const details = workbook.addWorksheet('时间线明细', {
    views: [{ state: 'frozen', ySplit: 4 }],
    properties: { tabColor: { argb: COLORS.header } }
  })

  buildRecordsSheet(records, applications, events, user)
  buildTimelineSheet(details, applications, events, user)

  const buffer = await workbook.xlsx.writeBuffer()
  downloadBlob(
    new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
    `${fileStem(user)}-投递时间线-${shanghaiDateKey()}.xlsx`
  )
}

function buildRecordsSheet(
  sheet: ExcelJS.Worksheet,
  applications: JobApplication[],
  events: JobEvent[],
  user: ExportUser
) {
  const columns = [
    { header: '序号', key: 'index', width: 8 },
    { header: '公司', key: 'company', width: 22 },
    { header: '岗位', key: 'position', width: 32 },
    { header: '地点', key: 'city', width: 16 },
    { header: '渠道', key: 'channel', width: 16 },
    { header: '投递日期', key: 'appliedDate', width: 14 },
    { header: '当前阶段', key: 'stage', width: 13 },
    { header: '当前状态', key: 'status', width: 13 },
    { header: '最新进展', key: 'latest', width: 24 },
    { header: '下一项安排', key: 'next', width: 28 },
    { header: '岗位备注', key: 'notes', width: 34 },
    { header: '时间线', key: 'timeline', width: 48 }
  ]
  sheet.columns = columns
  addSheetHeading(sheet, columns.length, '求职投递记录', user, applications.length, events.length)
  const header = sheet.getRow(4)
  header.values = columns.map(column => column.header)
  styleHeader(header)
  sheet.autoFilter = { from: 'A4', to: 'L4' }

  applications.forEach((application, index) => {
    const nodes = buildTimeline(application, events)
    const pending = nextPendingEvent(application, events)
    const latest = nodes[nodes.length - 1]
    const row = sheet.addRow({
      index: index + 1,
      company: text(application.company),
      position: text(application.position),
      city: text(application.city),
      channel: text(application.channel),
      appliedDate: text(application.appliedDate),
      stage: text(application.stage),
      status: text(application.status, ''),
      latest: latest ? `${latest.time || '当前'} ${latest.name}` : '—',
      next: pending ? `${eventTimeText(pending)} ${eventName(pending)}` : '—',
      notes: text(application.notes),
      timeline: nodes.map(node => `${node.time || '当前'} ${node.name}（${node.state}）`).join('\n')
    })
    row.height = Math.min(96, Math.max(30, nodes.length * 15))
    styleDataRow(row, index, stageFill(application))
    row.getCell('timeline').alignment = { vertical: 'top', wrapText: true }
    row.getCell('notes').alignment = { vertical: 'top', wrapText: true }
  })
  sheet.pageSetup = { orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 }
}

function buildTimelineSheet(
  sheet: ExcelJS.Worksheet,
  applications: JobApplication[],
  events: JobEvent[],
  user: ExportUser
) {
  const columns = [
    { header: '投递序号', key: 'applicationIndex', width: 12 },
    { header: '公司', key: 'company', width: 22 },
    { header: '岗位', key: 'position', width: 32 },
    { header: '节点顺序', key: 'nodeIndex', width: 12 },
    { header: '时间', key: 'time', width: 28 },
    { header: '时间类型', key: 'timeMode', width: 13 },
    { header: '节点名称', key: 'name', width: 24 },
    { header: '节点状态', key: 'state', width: 13 },
    { header: '地点／视频链接', key: 'location', width: 40 },
    { header: '日程备注', key: 'notes', width: 42 }
  ]
  sheet.columns = columns
  addSheetHeading(sheet, columns.length, '投递时间线明细', user, applications.length, events.length)
  const header = sheet.getRow(4)
  header.values = columns.map(column => column.header)
  styleHeader(header)
  sheet.autoFilter = { from: 'A4', to: 'J4' }

  applications.forEach((application, applicationIndex) => {
    const nodes = buildTimeline(application, events)
    nodes.forEach((node, nodeIndex) => {
      const row = sheet.addRow({
        applicationIndex: applicationIndex + 1,
        company: text(application.company),
        position: text(application.position),
        nodeIndex: nodeIndex + 1,
        time: node.time || '当前',
        timeMode: node.timeMode,
        name: node.name,
        state: node.state,
        location: node.location || '—',
        notes: node.notes || '—'
      })
      styleDataRow(row, applicationIndex, nodeFill(node.name))
      row.getCell('notes').alignment = { vertical: 'top', wrapText: true }
      const link = firstUrl(node.location)
      if (link) {
        row.getCell('location').value = { text: node.location, hyperlink: link }
        row.getCell('location').font = { color: { argb: '0563C1' }, underline: true }
      }
    })
  })
  sheet.pageSetup = { orientation: 'landscape', fitToPage: true, fitToWidth: 1, fitToHeight: 0 }
}

function addSheetHeading(
  sheet: ExcelJS.Worksheet,
  columnCount: number,
  title: string,
  user: ExportUser,
  applicationCount: number,
  eventCount: number
) {
  const lastColumn = sheet.getColumn(columnCount).letter
  sheet.mergeCells(`A1:${lastColumn}1`)
  sheet.getCell('A1').value = title
  sheet.getCell('A1').font = { bold: true, size: 18, color: { argb: COLORS.titleText } }
  sheet.getCell('A1').fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLORS.title } }
  sheet.getCell('A1').alignment = { vertical: 'middle' }
  sheet.getRow(1).height = 32
  sheet.mergeCells(`A2:${lastColumn}2`)
  sheet.getCell('A2').value = `账号：${user.displayName || user.email}（${user.email}）    导出日期：${shanghaiDateKey()}    共 ${applicationCount} 条投递、${eventCount} 项日程`
  sheet.getCell('A2').font = { size: 10, color: { argb: '536174' } }
  sheet.getCell('A2').alignment = { vertical: 'middle' }
  sheet.getRow(2).height = 24
  sheet.getRow(3).height = 8
}

function styleHeader(row: ExcelJS.Row) {
  row.height = 26
  row.eachCell(cell => {
    cell.font = { bold: true, color: { argb: COLORS.headerText } }
    cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COLORS.header } }
    cell.alignment = { vertical: 'middle', horizontal: 'center', wrapText: true }
    cell.border = thinBorder()
  })
}

function styleDataRow(row: ExcelJS.Row, index: number, marker: string) {
  row.eachCell({ includeEmpty: true }, (cell, columnNumber) => {
    cell.alignment = { vertical: 'top', wrapText: columnNumber > 1 }
    cell.border = thinBorder()
    cell.fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: columnNumber === 1 ? marker : (index % 2 ? COLORS.stripe : 'FFFFFF') }
    }
  })
}

function thinBorder(): Partial<ExcelJS.Borders> {
  const side = { style: 'thin' as const, color: { argb: COLORS.border } }
  return { top: side, left: side, bottom: side, right: side }
}

function buildTimeline(application: JobApplication, allEvents: JobEvent[]): TimelineNode[] {
  const applicationEvents = allEvents
    .filter(event => String(event.applicationId || '') === String(application.id || ''))
    .slice()
    .sort((left, right) => eventDeadline(left).localeCompare(eventDeadline(right)))
  const nodes: TimelineNode[] = [{
    order: 1,
    time: text(application.appliedDate),
    timeMode: '时间点',
    name: '已投递',
    state: applicationEvents.length || application.stage !== '已投递' ? '已完成' : '当前阶段',
    location: '',
    notes: ''
  }]
  applicationEvents.forEach(event => nodes.push({
    order: nodes.length + 1,
    time: eventTimeText(event),
    timeMode: eventEnd(event) ? '时间段' : '时间点',
    name: eventName(event),
    state: eventState(event),
    location: text(event.location),
    notes: text(event.notes)
  }))

  const status = text(application.status, '')
  const terminal = ['未通过', '已放弃', '已结束'].includes(status)
  const offer = application.stage === 'Offer' || status === '已通过'
  const enteredInterview = application.stage === '面试' || offer || applicationEvents.some(event => /面试|[一二三四五六七八九]面|HR/i.test(`${event.type || ''} ${event.title || ''}`))
  let statusLabel = offer ? 'Offer' : terminal ? status : status
  if (statusLabel === '等待结果' && !enteredInterview) statusLabel = ''
  const latestEvent = applicationEvents[applicationEvents.length - 1]
  const latestPending = latestEvent && !latestEvent.completed && !latestEvent.missed && !latestEvent.abandoned
  if (statusLabel && statusLabel !== nodes[nodes.length - 1]?.name && !(statusLabel === '等待结果' && latestPending)) {
    nodes.push({ order: nodes.length + 1, time: '当前', timeMode: '当前状态', name: statusLabel, state: '当前状态', location: '', notes: '' })
  } else if (!applicationEvents.length && application.stage && application.stage !== '已投递') {
    nodes.push({ order: nodes.length + 1, time: '当前', timeMode: '当前状态', name: text(application.stage), state: '当前阶段', location: '', notes: '' })
  }
  return nodes
}

function nextPendingEvent(application: JobApplication, allEvents: JobEvent[]) {
  const now = Date.now()
  return allEvents
    .filter(event => String(event.applicationId || '') === String(application.id || '') && !event.completed && !event.missed && !event.abandoned)
    .filter(event => timestamp(eventDeadline(event)) >= now)
    .sort((left, right) => timestamp(eventDeadline(left)) - timestamp(eventDeadline(right)))[0]
}

function compareApplications(left: JobApplication, right: JobApplication) {
  const rightDate = timestamp(right.appliedDate || right.createdAt)
  const leftDate = timestamp(left.appliedDate || left.createdAt)
  return rightDate - leftDate || String(left.company || '').localeCompare(String(right.company || ''), 'zh-CN')
}

function eventDeadline(event: JobEvent) {
  return String(event.endsAt || event.end || event.startsAt || event.start || event.date || '')
}

function eventStart(event: JobEvent) {
  return String(event.startsAt || event.start || event.date || '')
}

function eventEnd(event: JobEvent) {
  return String(event.endsAt || event.end || '')
}

function eventTimeText(event: JobEvent) {
  const start = scheduleText(eventStart(event))
  const end = scheduleText(eventEnd(event))
  return end ? `${start || '时间未填写'} ～ ${end}` : start || '时间未填写'
}

function eventName(event: JobEvent) {
  return text(event.title || event.type, '日程')
}

function eventState(event: JobEvent) {
  if (event.missed) return '已错过'
  if (event.abandoned) return '已放弃'
  if (event.completed) return '已完成'
  return timestamp(eventDeadline(event)) > Date.now() ? '待完成' : '待处理'
}

function scheduleText(value: string) {
  const source = value.trim()
  if (!source) return ''
  const wallTime = source.match(/^(\d{4}-\d{2}-\d{2})(?:[ T](\d{2}:\d{2}))?/)
  if (wallTime) return wallTime[2] ? `${wallTime[1]} ${wallTime[2]}` : wallTime[1]
  const date = new Date(source)
  if (Number.isNaN(date.getTime())) return source
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date)
}

function timestamp(value: unknown) {
  const time = new Date(String(value || '').replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : 0
}

function stageFill(application: JobApplication) {
  if (application.stage === 'Offer' || application.status === '已通过') return COLORS.offer
  if (['未通过', '已放弃', '已结束'].includes(text(application.status)) || application.stage === '已结束') return COLORS.stopped
  if (application.stage === '面试') return COLORS.interview
  if (application.stage === '笔试') return COLORS.test
  if (application.stage === '测评') return COLORS.assessment
  return COLORS.applied
}

function nodeFill(name: string) {
  if (/Offer|录用|通过/.test(name)) return COLORS.offer
  if (/未通过|错过|放弃|结束/.test(name)) return COLORS.stopped
  if (/面试|[一二三四五六七八九]面|HR/.test(name)) return COLORS.interview
  if (name.includes('笔试')) return COLORS.test
  if (name.includes('测评')) return COLORS.assessment
  return COLORS.applied
}

function firstUrl(value: string) {
  return value.match(/https?:\/\/[^\s]+/i)?.[0] || ''
}

function text(value: unknown, fallback = '—') {
  const result = String(value ?? '').trim()
  return result || fallback
}

function fileStem(user: ExportUser) {
  const label = user.displayName || user.email.split('@')[0] || 'job-tracker'
  return label.replace(/[<>:"/\\|?*\u0000-\u001f]+/g, '_').slice(0, 80) || 'job-tracker'
}

function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
