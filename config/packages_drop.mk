ifndef _DUMBOS_PACKAGES_DROP_MK_INCLUDED
_DUMBOS_PACKAGES_DROP_MK_INCLUDED := true

# ==============================================================================
# DumbOS Packages & Bloatware Replacement - Android 13 (lineage-20.0)
# ==============================================================================

# 1. Core DumbOS Userland (Custom Java DumbLauncher + SimpleKeyboard + AuroraStore + Markor)
PRODUCT_PACKAGES += \
    DumbLauncher \
    SimpleKeyboard \
    AuroraStore \
    Markor \
    dumbos-mode \
    dumbos-setup

# 2. Keep Quickstep Recents/Overview service matching device PRODUCT_TYPE
ifeq ($(PRODUCT_TYPE),go)
PRODUCT_PACKAGES += \
    TrebuchetQuickStepGo
else
PRODUCT_PACKAGES += \
    TrebuchetQuickStep
endif

# 3. Essential Lightweight Apps & Pure Black OLED Theme
PRODUCT_PACKAGES += \
    LineageBlackTheme \
    DeskClock \
    ExactCalculator \
    Gallery2 \
    DocumentsUI \
    CaptivePortalLogin \
    webview

ifneq ($(PRODUCT_NO_CAMERA),true)
PRODUCT_PACKAGES += \
    Aperture
endif

# 4. MicroG Open-Source GMS Replacement (added directly without WITH_GMS=true
#    so FDroid is not pulled alongside AuroraStore)
ifneq (,$(wildcard vendor/partner_gms/GmsCore/Android.mk))
PRODUCT_PACKAGES += \
    GmsCore \
    GsfProxy \
    FakeStore
endif

endif # _DUMBOS_PACKAGES_DROP_MK_INCLUDED
