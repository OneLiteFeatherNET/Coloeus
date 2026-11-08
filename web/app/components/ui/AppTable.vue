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
      <caption class="mb-2 text-left text-title-small text-on-surface">
        {{ caption }}
      </caption>
      <thead>
        <tr class="border-b border-outline-variant">
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            class="px-3 py-2 text-label-large text-on-surface-variant"
            :class="column.align === 'end' ? 'text-right' : 'text-left'"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="String(row[rowKey] ?? index)" class="border-b border-outline-variant">
          <td
            v-for="column in columns"
            :key="column.key"
            class="px-3 py-3 align-top text-body-medium text-on-surface"
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
        class="rounded-md bg-surface-container-low p-3 shadow-elevation-1"
      >
        <dl class="flex flex-col gap-2">
          <div v-for="column in columns" :key="column.key" class="flex flex-wrap justify-between gap-x-4">
            <dt class="text-label-large text-on-surface-variant">
              {{ column.label }}
            </dt>
            <dd class="text-end text-body-medium tabular-nums text-on-surface">
              <slot name="cell" :row="row" :column="column.key" />
            </dd>
          </div>
        </dl>
      </li>
    </ul>
  </div>
</template>
