<template>
  <div class="events-page">
    <section class="hero">
      <div class="hero-inner">
        <div class="breadcrumb">Home / News & Events / Event</div>
        <h1>Event</h1>
      </div>
    </section>

    <section class="filter-bar">
      <el-select v-model="draft.month" placeholder="Month" clearable>
        <el-option v-for="month in monthOptions" :key="month.value" :label="month.label" :value="month.value" />
      </el-select>
      <el-input
        v-model="draft.yearInput"
        placeholder="Year (e.g. 2026)"
        clearable
        maxlength="4"
        inputmode="numeric"
        class="year-input"
      />
      <el-select v-model="draft.type" placeholder="Types" clearable>
        <el-option label="Competition" value="competition" />
        <el-option label="Workshop" value="workshop" />
        <el-option label="Talk" value="talk" />
        <el-option label="Activity" value="activity" />
      </el-select>
      <el-input v-model="draft.keyword" placeholder="Keywords" clearable />
      <el-button
        v-if="authStore.isAdmin"
        plain
        class="manage-btn"
        @click="router.push('/admin/posts/events')"
      >
        Manage Events
      </el-button>
      <el-button type="primary" class="search-btn" @click="applyFilters">Search</el-button>
    </section>

    <section class="content-grid" v-loading="loading">
      <div class="events-column">
        <div v-if="upcomingEvents.length" class="section-block">
          <h2>Upcoming Events</h2>
          <article v-for="event in upcomingEvents" :key="event.id" class="event-row">
            <div class="event-date">
              <div class="month">{{ formatMonth(event.eventDate) }}</div>
              <div class="day">{{ formatDay(event.eventDate) }}</div>
              <div class="year">{{ formatYear(event.eventDate) }}</div>
            </div>
            <div class="event-info">
              <button class="event-title link-btn" @click="openEventDetail(event.id)">
                {{ event.title }}
              </button>
              <div class="event-type">{{ event.typeLabel }}</div>
            </div>
          </article>
        </div>

        <div v-if="pastEvents.length" class="section-block">
          <h2>Past Events</h2>
          <article v-for="event in pastEvents" :key="`past-${event.id}`" class="event-row">
            <div class="event-date">
              <div class="month">{{ formatMonth(event.eventDate) }}</div>
              <div class="day">{{ formatDay(event.eventDate) }}</div>
              <div class="year">{{ formatYear(event.eventDate) }}</div>
            </div>
            <div class="event-info">
              <button class="event-title link-btn" @click="openEventDetail(event.id)">
                {{ event.title }}
              </button>
              <div class="event-type">{{ event.typeLabel }}</div>
            </div>
          </article>
        </div>

        <p v-if="!upcomingEvents.length && !pastEvents.length && !loading" class="no-results">
          No results have been found.
        </p>
      </div>

      <aside class="calendar-card">
        <div class="calendar-head">
          <button type="button" @click="prevCalendarMonth">&#8592;</button>
          <div>{{ calendarTitle }}</div>
          <button type="button" @click="nextCalendarMonth">&#8594;</button>
        </div>
        <div class="calendar-weekdays">
          <span v-for="weekday in weekdays" :key="weekday">{{ weekday }}</span>
        </div>
        <div class="calendar-grid">
          <span v-for="(cell, idx) in calendarCells" :key="idx" :class="{ muted: !cell.currentMonth, marked: cell.hasEvent }">
            {{ cell.day || '' }}
          </span>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listPosts } from '../api/postApi'
import { useAuthStore } from '../stores/auth'

const loading = ref(false)
const events = ref([])
const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const calendarMonth = ref(startOfMonth(new Date()))
const router = useRouter()
const authStore = useAuthStore()

const draft = reactive({
  month: null,
  yearInput: '',
  type: '',
  keyword: ''
})

const applied = reactive({
  month: null,
  year: null,
  type: '',
  keyword: ''
})

const monthOptions = [
  { value: 1, label: 'January' },
  { value: 2, label: 'February' },
  { value: 3, label: 'March' },
  { value: 4, label: 'April' },
  { value: 5, label: 'May' },
  { value: 6, label: 'June' },
  { value: 7, label: 'July' },
  { value: 8, label: 'August' },
  { value: 9, label: 'September' },
  { value: 10, label: 'October' },
  { value: 11, label: 'November' },
  { value: 12, label: 'December' }
]

const filteredEvents = computed(() => {
  const keyword = applied.keyword.trim().toLowerCase()
  return events.value.filter((event) => {
    if (applied.month && event.eventDate.getMonth() + 1 !== applied.month) return false
    if (applied.year && event.eventDate.getFullYear() !== applied.year) return false
    if (applied.type && event.type !== applied.type) return false
    if (keyword && !`${event.title} ${event.content}`.toLowerCase().includes(keyword)) return false
    return true
  })
})

const upcomingEvents = computed(() => {
  const now = startOfDay(new Date())
  return filteredEvents.value
    .filter((event) => startOfDay(event.eventDate) >= now)
    .sort((a, b) => a.eventDate - b.eventDate)
})

const pastEvents = computed(() => {
  const now = startOfDay(new Date())
  return filteredEvents.value
    .filter((event) => startOfDay(event.eventDate) < now)
    .sort((a, b) => b.eventDate - a.eventDate)
})

const calendarTitle = computed(() => {
  const month = monthOptions[calendarMonth.value.getMonth()].label
  return `${month} ${calendarMonth.value.getFullYear()}`
})

const calendarCells = computed(() => {
  const firstDay = startOfMonth(calendarMonth.value)
  const startWeekday = firstDay.getDay()
  const daysInMonth = new Date(firstDay.getFullYear(), firstDay.getMonth() + 1, 0).getDate()
  const cells = []
  for (let i = 0; i < startWeekday; i += 1) {
    cells.push({ day: '', currentMonth: false, hasEvent: false })
  }
  for (let day = 1; day <= daysInMonth; day += 1) {
    const date = new Date(firstDay.getFullYear(), firstDay.getMonth(), day)
    cells.push({
      day,
      currentMonth: true,
      hasEvent: filteredEvents.value.some((event) => isSameDay(event.eventDate, date))
    })
  }
  while (cells.length % 7 !== 0) {
    cells.push({ day: '', currentMonth: false, hasEvent: false })
  }
  return cells
})

onMounted(loadEvents)

async function loadEvents() {
  loading.value = true
  try {
    const rows = await listPosts({ type: 'EVENT', limit: 100 })
    events.value = rows.map((item) => {
      const date = parseDate(item.eventStartAt || item.createdAt)
      return {
        id: item.id,
        title: item.title,
        content: item.content || '',
        eventDate: date,
        eventEndDate: parseDate(item.eventEndAt || item.eventStartAt || item.createdAt),
        type: detectType(item),
        typeLabel: toTypeLabel(detectType(item))
      }
    })
  } catch (error) {
    ElMessage.error(error?.message || 'Failed to load events')
  } finally {
    loading.value = false
  }
}

function appliedYearFromInput() {
  const raw = String(draft.yearInput ?? '').trim()
  if (!raw) return null
  if (!/^\d{4}$/.test(raw)) return null
  const y = Number(raw)
  if (y < 1 || y > 9999) return null
  return y
}

function applyFilters() {
  applied.month = draft.month
  const yearTrim = String(draft.yearInput ?? '').trim()
  if (yearTrim && !/^\d{4}$/.test(yearTrim)) {
    ElMessage.warning('Year must be four digits (e.g. 2026), or leave empty.')
    return
  }
  applied.year = appliedYearFromInput()
  applied.type = draft.type
  applied.keyword = draft.keyword
}

function prevCalendarMonth() {
  calendarMonth.value = new Date(calendarMonth.value.getFullYear(), calendarMonth.value.getMonth() - 1, 1)
}

function nextCalendarMonth() {
  calendarMonth.value = new Date(calendarMonth.value.getFullYear(), calendarMonth.value.getMonth() + 1, 1)
}

function detectType(item) {
  const category = String(item.eventCategory || '').trim().toLowerCase()
  if (category) {
    if (category.includes('competition')) return 'competition'
    if (category.includes('workshop')) return 'workshop'
    if (category.includes('seminar') || category.includes('talk')) return 'talk'
    return 'activity'
  }
  const text = `${item.title || ''} ${item.summary || ''} ${item.content || ''}`.toLowerCase()
  if (text.includes('competition')) return 'competition'
  if (text.includes('workshop')) return 'workshop'
  if (text.includes('talk') || text.includes('seminar')) return 'talk'
  return 'activity'
}

function toTypeLabel(type) {
  if (type === 'competition') return 'Competition'
  if (type === 'workshop') return 'Workshop'
  if (type === 'talk') return 'Talk'
  return 'Activity'
}

function parseDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return new Date()
  return date
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function formatMonth(date) {
  return monthOptions[date.getMonth()].label.slice(0, 3)
}

function formatDay(date) {
  return String(date.getDate()).padStart(2, '0')
}

function formatYear(date) {
  return date.getFullYear()
}

function openEventDetail(id) {
  router.push({ name: 'event-detail', params: { id } })
}
</script>

<style scoped>
.events-page {
  margin: -30px auto 0;
}

.hero {
  background:
    linear-gradient(140deg, rgba(141, 21, 58, 0.08) 0%, rgba(141, 21, 58, 0.02) 45%, rgba(0, 0, 0, 0.04) 45%, rgba(0, 0, 0, 0.02) 100%),
    #f2f3f5;
  border-left: 8px solid #8d153a;
  min-height: 180px;
  display: flex;
  align-items: center;
}

.hero-inner {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
}

.breadcrumb {
  color: #8c8c8c;
  font-size: 12px;
}

h1 {
  margin: 10px 0 0;
  font-size: 52px;
  color: #8d153a;
  font-weight: 500;
}

.filter-bar {
  margin: 18px auto 0;
  max-width: 1200px;
  padding: 0 12px;
  display: grid;
  grid-template-columns: 1fr 1fr 1.1fr 1.4fr auto auto;
  gap: 10px;
}

.search-btn {
  background: #8d153a;
  border-color: #8d153a;
}

.manage-btn {
  border-color: #8d153a;
  color: #8d153a;
}

.content-grid {
  max-width: 1200px;
  margin: 28px auto 0;
  padding: 0 12px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 34px;
}

.section-block + .section-block {
  margin-top: 26px;
}

h2 {
  margin: 0 0 14px;
  font-size: 50px;
  line-height: 1;
  color: #8d153a;
  font-weight: 400;
}

.event-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 16px;
  border-bottom: 1px solid #e5e5e5;
  padding: 14px 0;
}

.event-date {
  text-align: center;
  color: #555;
}

.event-date .month {
  font-size: 16px;
}

.event-date .day {
  font-size: 34px;
  line-height: 1.1;
  color: #8d153a;
}

.event-date .year {
  font-size: 14px;
}

.event-title {
  font-size: 28px;
  color: #333;
}

.link-btn {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: left;
  text-decoration: underline;
}

.link-btn:hover {
  color: #8d153a;
}

.event-type {
  margin-top: 6px;
  color: #7d7d7d;
  font-size: 18px;
}

.no-results {
  color: #555;
  margin: 8px 0 0;
  font-size: 16px;
}

.calendar-card {
  background: #f4f4f4;
  border: 1px solid #ededed;
  padding: 16px;
  height: fit-content;
}

.calendar-head {
  display: grid;
  grid-template-columns: 36px 1fr 36px;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.calendar-head button {
  border: none;
  background: transparent;
  color: #8d153a;
  font-size: 22px;
  cursor: pointer;
}

.calendar-head div {
  text-align: center;
  font-size: 34px;
  color: #8d153a;
}

.calendar-weekdays,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.calendar-weekdays span {
  text-align: center;
  color: #777;
  font-size: 14px;
}

.calendar-grid span {
  min-height: 30px;
  display: grid;
  place-items: center;
  background: #fff;
  color: #555;
  font-size: 14px;
}

.calendar-grid span.muted {
  background: transparent;
}

.calendar-grid span.marked {
  border: 1px solid #8d153a;
  color: #8d153a;
}

@media (max-width: 960px) {
  .events-page {
    margin-top: -16px;
  }

  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .calendar-card {
    order: -1;
  }

  h1 {
    font-size: 38px;
  }

  h2 {
    font-size: 42px;
  }

  .event-title {
    font-size: 22px;
  }
}
</style>
