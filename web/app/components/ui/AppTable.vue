<script setup lang="ts">
// A data table. From the `md` breakpoint up it is a table with a caption and `scope` on header cells; below it every
// row becomes a stacked list of label and value. Cells come from the `cell` slot, so both layouts share one source.
export interface AppTableColumn {
  key: string
  label: string
  align?: 'start' | 'end'
}

defineProps<{
  caption: string
  columns: AppTableColumn[]
  rows: readonly Record<string, unknown>[]
  rowKey: string
}>()
</script>

<template>
  <div>
    <table class="hidden w-full border-collapse text-left md:table">
      <caption class="mb-2 text-left font-medium">
        {{ caption }}
      </caption>
      <thead>
        <tr class="border-b border-(--c-border)">
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            class="px-3 py-2 font-semibold"
            :class="column.align === 'end' ? 'text-right' : 'text-left'"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="String(row[rowKey] ?? index)" class="border-b border-(--c-border)">
          <td
            v-for="column in columns"
            :key="column.key"
            class="px-3 py-2 align-top"
            :class="column.align === 'end' ? 'text-right tabular-nums' : 'text-left'"
          >
            <slot name="cell" :row="row" :column="column.key" />
          </td>
        </tr>
      </tbody>
    </table>
    <ul class="flex flex-col gap-3 md:hidden" :aria-label="caption">
      <li
        v-for="(row, index) in rows"
        :key="String(row[rowKey] ?? index)"
        class="rounded-lg border border-(--c-border) bg-(--c-surface) p-3"
      >
        <dl class="flex flex-col gap-2">
          <div v-for="column in columns" :key="column.key" class="flex flex-wrap justify-between gap-x-4">
            <dt class="font-medium text-(--c-muted)">
              {{ column.label }}
            </dt>
            <dd class="text-end tabular-nums">
              <slot name="cell" :row="row" :column="column.key" />
            </dd>
          </div>
        </dl>
      </li>
    </ul>
  </div>
</template>
