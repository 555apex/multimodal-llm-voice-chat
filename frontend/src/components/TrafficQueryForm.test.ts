import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TrafficQueryForm from './TrafficQueryForm.vue'

describe('TrafficQueryForm', () => {
  it('emits the default teaching query', async () => {
    const wrapper = mount(TrafficQueryForm, { props: { loading: false } })

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      areaCode: '350100',
      roadName: '五四路',
      direction: '南向北',
    })
  })

  it('blocks an invalid area code', async () => {
    const wrapper = mount(TrafficQueryForm, { props: { loading: false } })
    await wrapper.find('input').setValue('福州')

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('必须是6位数字')
  })
})
