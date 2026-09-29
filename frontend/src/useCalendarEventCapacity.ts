import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type CalendarLaneEntry = { lane: number }

const fallbackCapacity = 3

export function useCalendarEventCapacity() {
  const calendarGrid = ref<HTMLElement | null>(null)
  const capacityByDate = ref<Record<string, number>>({})
  let resizeObserver: ResizeObserver | null = null
  let resizeFrame = 0

  function measureCapacities() {
    resizeFrame = 0
    const grid = calendarGrid.value
    if (!grid) return

    const capacities: Record<string, number> = {}
    grid.querySelectorAll<HTMLElement>('[data-calendar-date]').forEach(cell => {
      const date = cell.dataset.calendarDate
      const eventArea = cell.querySelector<HTMLElement>('.day-events')
      if (!date || !eventArea) return

      const styles = window.getComputedStyle(eventArea)
      const rowHeight = Number.parseFloat(styles.getPropertyValue('--calendar-event-row-height'))
        || Number.parseFloat(styles.gridAutoRows)
        || 1
      const rowGap = Number.parseFloat(styles.rowGap) || 0
      capacities[date] = Math.max(0, Math.floor((eventArea.clientHeight + rowGap) / (rowHeight + rowGap)))
    })
    capacityByDate.value = capacities
  }

  function recalculateCalendarCapacity() {
    if (resizeFrame) window.cancelAnimationFrame(resizeFrame)
    resizeFrame = window.requestAnimationFrame(measureCapacities)
  }

  function capacityFor(date: string, entryCount: number) {
    const availableRows = capacityByDate.value[date] ?? fallbackCapacity
    return entryCount > availableRows ? Math.max(0, availableRows - 1) : availableRows
  }

  function visibleCalendarEntries<T extends CalendarLaneEntry>(date: string, entries: T[]) {
    const capacity = capacityFor(date, entries.length)
    return [...entries].sort((left, right) => left.lane - right.lane).slice(0, capacity)
  }

  function hiddenCalendarEntryCount<T extends CalendarLaneEntry>(date: string, entries: T[]) {
    return Math.max(0, entries.length - capacityFor(date, entries.length))
  }

  watch(calendarGrid, (current, previous) => {
    if (previous) resizeObserver?.unobserve(previous)
    if (current) resizeObserver?.observe(current)
    nextTick(recalculateCalendarCapacity)
  }, { flush: 'post' })

  onMounted(() => {
    if (typeof ResizeObserver !== 'undefined') resizeObserver = new ResizeObserver(recalculateCalendarCapacity)
    if (calendarGrid.value) resizeObserver?.observe(calendarGrid.value)
    window.addEventListener('resize', recalculateCalendarCapacity, { passive: true })
    nextTick(recalculateCalendarCapacity)
  })

  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    window.removeEventListener('resize', recalculateCalendarCapacity)
    if (resizeFrame) window.cancelAnimationFrame(resizeFrame)
  })

  return {
    calendarGrid,
    hiddenCalendarEntryCount,
    recalculateCalendarCapacity,
    visibleCalendarEntries
  }
}
