// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

import { presentationForRoute } from '../navigation/route-presentation'
import type { GlobalThemeOverrides } from 'naive-ui'
import { naiveTheme, themes } from './tokens'
import type { ThemeMode } from '../preferences/preferences'

export function isCompactRoute(name: unknown) {
  return presentationForRoute(name).compact
}

// Approved compact visual baseline for auth, user management and audit surfaces.
export const compactThemes = {
  light: { ...themes.light, background: '#F1F5FC', glass: 'rgba(255,255,255,0.88)' },
  dark: {
    ...themes.dark,
    background: '#1B2B46',
    panel: '#263B58',
    overlay: '#2A405D',
    glass: 'rgba(38,59,88,0.92)',
    border: '#506786',
    controlBorder: '#8B9EBB',
    muted: '#BDCCE2',
    selected: '#304D73',
    glow: 'rgba(108,157,255,0.19)',
  },
} as const

export function compactTheme(mode: ThemeMode): GlobalThemeOverrides {
  const base = naiveTheme(mode)
  const t = compactThemes[mode]
  return {
    ...base,
    common: {
      ...base.common,
      fontSize: '13px',
      heightSmall: '30px',
      heightMedium: '32px',
      heightLarge: '38px',
      textColor2: t.muted,
      textColor3: t.muted,
      cardColor: t.panel,
      modalColor: t.overlay,
      inputColor: t.panel,
      popoverColor: t.overlay,
      borderColor: t.controlBorder,
    },
    Input: {
      ...base.Input,
      color: t.panel,
      colorFocus: t.panel,
      border: `1px solid ${t.controlBorder}`,
      fontSizeSmall: '13px',
      fontSizeMedium: '13px',
    },
    Form: {
      labelTextColor: t.text,
      labelFontWeight: '500',
      labelFontSizeTopMedium: '13px',
      lineHeight: '1.5',
      labelHeightMedium: '24px',
      labelPaddingVertical: '0 0 5px 0',
    },
    Button: {
      ...base.Button,
      heightMedium: '36px',
      fontSizeSmall: '13px',
      fontSizeMedium: '13px',
      fontWeight: '500',
    },
    Checkbox: { ...base.Checkbox, fontSizeSmall: '13px', fontSizeMedium: '13px' },
    Drawer: {
      color: t.panel,
      textColor: t.text,
      titleTextColor: t.text,
      titleFontSize: '18px',
      titleFontWeight: '600',
      lineHeight: '1.5',
      headerPadding: '10px 22px',
      bodyPadding: '18px 22px',
      footerPadding: '14px 22px',
      headerBorderBottom: `1px solid ${t.border}`,
      footerBorderTop: `1px solid ${t.border}`,
    },
    DatePicker: {
      itemFontSize: '13px',
      calendarDaysFontSize: '13px',
      calendarTitleFontSize: '13px',
      panelColor: t.overlay,
      panelTextColor: t.text,
      itemTextColor: t.text,
      calendarTitleTextColor: t.text,
      calendarDaysTextColor: t.muted,
      panelBoxShadow: 'none',
      panelBorderRadius: '0',
      peers: { Input: { fontSizeSmall: '13px', fontSizeMedium: '13px' } },
    },
    TimePicker: {
      itemFontSize: '13px',
      panelColor: t.overlay,
      itemTextColor: t.text,
      peers: { Input: { fontSizeSmall: '13px', fontSizeMedium: '13px' } },
    },
    Select: {
      peers: {
        InternalSelection: {
          color: t.panel,
          textColor: t.text,
          border: `1px solid ${t.controlBorder}`,
          fontSizeSmall: '13px',
          fontSizeMedium: '13px',
        },
        InternalSelectMenu: {
          color: t.overlay,
          optionTextColor: t.text,
          optionFontSizeSmall: '13px',
          optionFontSizeMedium: '13px',
        },
      },
    },
  }
}
