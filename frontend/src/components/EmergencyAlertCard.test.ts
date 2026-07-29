import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmergencyAlertCard from './EmergencyAlertCard.vue'
import type { EmergencyAlert } from '../types/dispatch'

const alert: EmergencyAlert = {
  event: {
    eventId: '202607280000000001',
    customId: 'AGT20260728EVT000000000000000001',
    occurrenceTime: '2026-07-28T00:00:00Z',
    eventType: 'DT01',
    eventTypeName: '崩塌',
    description: '福州市某道路发生边坡崩塌',
  },
  pendingCount: 19,
}

describe('EmergencyAlertCard', () => {
  it('starts official dispatch generation from the alert', async () => {
    const wrapper = mount(EmergencyAlertCard, {
      props: { alert, busy: false, errorMessage: '' },
    })

    await wrapper.find('.generate-dispatch-button').trigger('click')

    expect(wrapper.emitted('generate')).toHaveLength(1)
    expect(wrapper.text()).toContain('当前还有 19 条事件待处理')
  })

  it('requires a reason and a second confirmation before no-dispatch', async () => {
    const wrapper = mount(EmergencyAlertCard, {
      props: { alert, busy: false, errorMessage: '' },
    })

    await wrapper.find('.no-dispatch-button').trigger('click')
    await wrapper.find('#no-dispatch-reason').setValue('现场已经自行恢复')
    await wrapper.find('.danger-confirm-button').trigger('click')

    expect(wrapper.text()).toContain('请再次确认')
    expect(wrapper.emitted('noDispatch')).toBeUndefined()

    await wrapper.find('.danger-confirm-button').trigger('click')
    expect(wrapper.emitted('noDispatch')?.[0]).toEqual(['现场已经自行恢复'])
  })
})
