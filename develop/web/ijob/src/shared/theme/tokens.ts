// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import type { GlobalThemeOverrides } from 'naive-ui'
import type { ThemeMode } from '../preferences/preferences'

export const themes = {
  light: {
    background: '#F3F6FC',
    panel: '#FFFFFF',
    glass: 'rgba(255,255,255,0.78)',
    overlay: '#FFFFFF',
    text: '#24324B',
    muted: '#52627B',
    border: '#D3DFF1',
    controlBorder: '#74849E',
    primary: '#3064DA',
    primaryHover: '#2857C7',
    onPrimary: '#FFFFFF',
    focus: '#245BCE',
    success: '#087653',
    warning: '#916000',
    error: '#BF304D',
    selected: '#E4EDFF',
    disabled: '#EDF0F6',
    glow: 'rgba(111,163,255,0.15)',
  },
  dark: {
    background: '#101D38',
    panel: '#192C49',
    glass: 'rgba(25,44,73,0.84)',
    overlay: '#203451',
    text: '#EDF3FF',
    muted: '#B6C6E0',
    border: '#405778',
    controlBorder: '#8298BA',
    primary: '#8DB3FF',
    primaryHover: '#ACCAFF',
    onPrimary: '#101D38',
    focus: '#AFCBFF',
    success: '#6FE0B7',
    warning: '#F2CD79',
    error: '#FF9AAE',
    selected: '#2C4670',
    disabled: '#273851',
    glow: 'rgba(100,148,255,0.17)',
  },
} as const

export function naiveTheme(mode: ThemeMode): GlobalThemeOverrides {
  const t = themes[mode]
  return {
    common: {
      primaryColor: t.primary,
      primaryColorHover: t.primaryHover,
      primaryColorPressed: t.primary,
      primaryColorSuppl: t.primary,
      textColorBase: t.text,
      textColor1: t.text,
      textColor2: t.muted,
      textColor3: t.muted,
      placeholderColor: t.muted,
      bodyColor: t.background,
      cardColor: t.panel,
      modalColor: t.overlay,
      popoverColor: t.overlay,
      inputColor: t.panel,
      borderColor: t.controlBorder,
      dividerColor: t.border,
      actionColor: t.selected,
      successColor: t.success,
      infoColor: t.primary,
      warningColor: t.warning,
      errorColor: t.error,
      fontSize: '14px',
      borderRadius: '8px',
      heightMedium: '36px',
      fontFamily: 'Inter, "Segoe UI", "Microsoft YaHei", sans-serif',
    },
    Input: {
      color: t.panel,
      colorFocus: t.panel,
      textColor: t.text,
      border: `1px solid ${t.controlBorder}`,
      borderFocus: `1px solid ${t.focus}`,
      borderHover: `1px solid ${t.primary}`,
    },
    Button: {
      textColorPrimary: t.onPrimary,
      textColorHoverPrimary: t.onPrimary,
      textColorPressedPrimary: t.onPrimary,
    },
  }
}

// One bridge for future lazy-loaded ECharts / Vue Flow / CodeMirror adapters.
export function visualTheme(mode: ThemeMode) {
  const t = themes[mode]
  return {
    background: t.panel,
    foreground: t.text,
    grid: t.border,
    selection: t.selected,
    cursor: t.focus,
    series: [t.primary, t.success, t.warning, t.error],
  }
}
