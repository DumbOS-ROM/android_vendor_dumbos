ifndef _DUMBOS_PACKAGES_DROP_MK_INCLUDED
_DUMBOS_PACKAGES_DROP_MK_INCLUDED := true

# ==============================================================================
# LineageDumb Packages & Bloatware Replacement - Android 15 (lineage-22.2)
# ==============================================================================
# NOTE: `filter-out` across inherit-product boundaries does NOT remove packages
# requested by vendor/lineage/config/*. All bloat, CJK font, and ML classifier
# removals are executed cleanly via `overrides: [...]` in prebuilt/Android.bp.
# ==============================================================================

# 1. Core DumbOS Userland
PRODUCT_PACKAGES += \
    Olauncher \
    SimpleKeyboard \
    AuroraStore \
    Markor \
    dumbos-mode \
    dumbos-setup

# 2. Preserve Quickstep Recents/Overview service matching device PRODUCT_TYPE
#    (Olauncher handles HOME role via dumbos-setup; Quickstep handles Alt-Tab/Recents)
ifeq ($(PRODUCT_TYPE),go)
PRODUCT_PACKAGES += \
    TrebuchetQuickStepGo
else
PRODUCT_PACKAGES += \
    TrebuchetQuickStep
endif

# 3. Essential Lightweight Apps & Pure Black Theme
PRODUCT_PACKAGES += \
    LineageBlackTheme \
    DeskClock \
    ExactCalculator \
    Glimpse \
    DocumentsUI \
    CaptivePortalLogin \
    webview

ifneq ($(PRODUCT_NO_CAMERA),true)
PRODUCT_PACKAGES += \
    Aperture
endif

# 4. MicroG Open-Source GMS Replacement (added directly without WITH_GMS=true so
#    FDroid is not pulled alongside AuroraStore)
ifneq (,$(wildcard vendor/partner_gms/GmsCore/Android.mk))
PRODUCT_PACKAGES += \
    GmsCore \
    GsfProxy \
    FakeStore
endif

endif # _DUMBOS_PACKAGES_DROP_MK_INCLUDED
