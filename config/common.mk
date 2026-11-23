ifndef _DUMBOS_COMMON_MK_INCLUDED
_DUMBOS_COMMON_MK_INCLUDED := true

# ==============================================================================
# LineageDumb (DumbOS) Product Integration - Android 15 (lineage-22.2)
# ==============================================================================

# 1. Inherit safe properties & package replacements
$(call inherit-product, vendor/dumbos/config/props.mk)
$(call inherit-product, vendor/dumbos/config/packages_drop.mk)

# 2. Prepend DumbOS resource overlays so OLED Pure Black wallpaper, Settings defaults,
#    core/res & SystemUI optimizations take precedence over vendor/lineage & device overlays.
PRODUCT_PACKAGE_OVERLAYS := vendor/dumbos/overlay $(PRODUCT_PACKAGE_OVERLAYS)
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += vendor/dumbos/overlay

# 3. Default fallback locales (preserves device PRODUCT_LOCALES if already defined)
PRODUCT_LOCALES ?= en_US id_ID

# 4. Tune dexopt filters ONLY if the device already enables WITH_DEXPREOPT
#    (Never force WITH_DEXPREOPT on small-/system legacy devices.)
ifeq ($(WITH_DEXPREOPT),true)
PRODUCT_SYSTEM_SERVER_COMPILER_FILTER ?= speed-profile
PRODUCT_DEX_PREOPT_DEFAULT_COMPILER_FILTER ?= verify
endif

# 5. Init & one-shot boot initialization (/product/etc/init/init.dumbos.rc)
PRODUCT_COPY_FILES += \
    vendor/dumbos/prebuilt/etc/init/init.dumbos.rc:$(TARGET_COPY_OUT_PRODUCT)/etc/init/init.dumbos.rc

# 6. Custom System Font: OnePlus Slate (Android 14 uses both fonts.xml & font_fallback.xml;
#    both Soong modules are overridden in prebuilt/Android.bp to prevent Ninja duplicates)
PRODUCT_COPY_FILES += \
    vendor/dumbos/prebuilt/fonts/fonts.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/fonts.xml \
    vendor/dumbos/prebuilt/fonts/font_fallback.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/font_fallback.xml \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Black.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Black.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Bold.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Bold.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Light.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Light.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Medium.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Medium.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Regular.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Regular.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/OnePlusSlate-Thin.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/OnePlusSlate-Thin.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Black.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Black.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Bold.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Bold.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Book.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Book.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Light.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Light.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Medium.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Medium.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Regular.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Regular.ttf \
    vendor/dumbos/prebuilt/fonts/ttf/SlateForOnePlus-Thin.ttf:$(TARGET_COPY_OUT_SYSTEM)/fonts/SlateForOnePlus-Thin.ttf

# 7. Whitelist /system font additions for modern devices enforcing artifact path checks
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system/etc/fonts.xml \
    system/etc/font_fallback.xml \
    system/fonts/OnePlusSlate-Black.ttf \
    system/fonts/OnePlusSlate-Bold.ttf \
    system/fonts/OnePlusSlate-Light.ttf \
    system/fonts/OnePlusSlate-Medium.ttf \
    system/fonts/OnePlusSlate-Regular.ttf \
    system/fonts/OnePlusSlate-Thin.ttf \
    system/fonts/SlateForOnePlus-Black.ttf \
    system/fonts/SlateForOnePlus-Bold.ttf \
    system/fonts/SlateForOnePlus-Book.ttf \
    system/fonts/SlateForOnePlus-Light.ttf \
    system/fonts/SlateForOnePlus-Medium.ttf \
    system/fonts/SlateForOnePlus-Regular.ttf \
    system/fonts/SlateForOnePlus-Thin.ttf

endif # _DUMBOS_COMMON_MK_INCLUDED
