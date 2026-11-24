ifndef _DUMBOS_PACKAGES_DROP_MK_INCLUDED
_DUMBOS_PACKAGES_DROP_MK_INCLUDED := true

# ==============================================================================
# DumbOS Packages & Bloatware Replacement - Android 14 (lineage-21.0)
# ==============================================================================

# 1. Core DumbOS Userland (Custom Java DumbLauncher + SimpleKeyboard + AuroraStore + Markor)
PRODUCT_PACKAGES += \
    DumbLauncher \
    SimpleKeyboard \
    AuroraStore \
    Markor \
    dumbos-mode \
    dumbos-setup

# 3. Essential Lightweight Apps & Pure Black OLED Theme
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

# 4. MicroG Open-Source GMS Replacement (added directly without WITH_GMS=true
#    so FDroid is not pulled alongside AuroraStore)
ifneq (,$(wildcard vendor/partner_gms/GmsCore/Android.mk))
PRODUCT_PACKAGES += \
    GmsCore \
    GsfProxy \
    FakeStore
endif

endif # _DUMBOS_PACKAGES_DROP_MK_INCLUDED
