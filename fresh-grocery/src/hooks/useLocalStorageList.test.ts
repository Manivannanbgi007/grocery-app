import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { resetLocalStorageLists, useLocalStorageList } from './useLocalStorageList'

interface Item {
  id: number
  name: string
}

const KEY = 'test.list'
const useTestList = () => useLocalStorageList<Item>(KEY, 3, (item) => item.id)

describe('useLocalStorageList', () => {
  it('adds new items first, without duplicates, and keeps at most maxItems', () => {
    const { result } = renderHook(useTestList)

    act(() => result.current.add({ id: 1, name: 'Tomato' }))
    act(() => result.current.add({ id: 2, name: 'Onion' }))
    act(() => result.current.add({ id: 1, name: 'Tomato' })) // already there: moves to the front
    expect(result.current.items.map((item) => item.id)).toEqual([1, 2])

    act(() => result.current.add({ id: 3, name: 'Potato' }))
    act(() => result.current.add({ id: 4, name: 'Banana' })) // a 4th item pushes out the oldest
    expect(result.current.items.map((item) => item.id)).toEqual([4, 3, 1])
    expect(result.current.has(2)).toBe(false)
  })

  it('removes and clears', () => {
    const { result } = renderHook(useTestList)
    act(() => result.current.add({ id: 1, name: 'Tomato' }))
    act(() => result.current.add({ id: 2, name: 'Onion' }))

    act(() => result.current.remove(1))
    expect(result.current.items).toEqual([{ id: 2, name: 'Onion' }])

    act(() => result.current.clear())
    expect(result.current.items).toEqual([])
  })

  it('shares one list between components and saves it in localStorage', () => {
    const first = renderHook(useTestList)
    const second = renderHook(useTestList)

    act(() => first.result.current.add({ id: 7, name: 'Milk' }))

    expect(second.result.current.items).toEqual([{ id: 7, name: 'Milk' }])
    expect(JSON.parse(localStorage.getItem(KEY) ?? '[]')).toEqual([{ id: 7, name: 'Milk' }])
  })

  it('loads a saved list and ignores a broken one', () => {
    localStorage.setItem(KEY, JSON.stringify([{ id: 5, name: 'Curd' }]))
    expect(renderHook(useTestList).result.current.items).toEqual([{ id: 5, name: 'Curd' }])

    resetLocalStorageLists()
    localStorage.setItem(KEY, '{not json')
    expect(renderHook(useTestList).result.current.items).toEqual([])
  })
})
