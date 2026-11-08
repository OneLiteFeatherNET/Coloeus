<script setup lang="ts">
import { computed, useId } from 'vue'
import { barHeights } from '~/utils/chart'

// Bars per label as inline SVG. Each series has its own fill and its own pattern, so that the series are told apart
// without color. The chart is one image with an accessible name; the table alternative is the caller's business.
export interface AppBarSeries {
  name: string
  values: string[]
  pattern: 'solid' | 'hatched'
}

const props = defineProps<{
  label: string
  labels: string[]
  series: AppBarSeries[]
}>()

const width = 640
const height = 240
const plot = 200
const groupWidth = computed(() => width / Math.max(1, props.labels.length))
const hatchId = `hatch-${useId()}`

const bars = computed(() => props.series.map((serie, index) => ({
  name: serie.name,
  pattern: serie.pattern,
  index,
  heights: barHeights(serie.values, plot)
})))
</script>

<template>
  <div class="flex flex-col gap-3">
    <svg
      role="img"
      :aria-label="label"
      :viewBox="`0 0 ${width} ${height}`"
      class="h-auto w-full"
      preserveAspectRatio="none"
    >
      <defs>
        <pattern :id="hatchId" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="3" height="6" fill="currentColor" />
        </pattern>
      </defs>
      <g v-for="(serie, seriesIndex) in bars" :key="serie.name">
        <rect
          v-for="(value, index) in serie.heights"
          :key="index"
          :x="index * groupWidth + groupWidth * 0.15 + seriesIndex * (groupWidth * 0.35)"
          :y="plot - value"
          :width="groupWidth * 0.32"
          :height="value"
          :class="seriesIndex === 0 ? 'text-primary' : 'text-tertiary'"
          :fill="serie.pattern === 'hatched' ? `url(#${hatchId})` : 'currentColor'"
        />
      </g>
      <line x1="0" :y1="plot" :x2="width" :y2="plot" stroke="currentColor" class="text-outline" />
    </svg>
    <ul class="flex flex-wrap gap-4 text-sm">
      <li v-for="(serie, seriesIndex) in series" :key="serie.name" class="flex items-center gap-2">
        <span
          aria-hidden="true"
          class="inline-block h-3 w-3 border"
          :class="seriesIndex === 0 ? 'bg-primary border-primary' : 'bg-tertiary border-tertiary'"
        />
        {{ serie.name }}
      </li>
    </ul>
  </div>
</template>
