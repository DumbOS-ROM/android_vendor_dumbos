ifndef _DUMBOS_PROPS_MK_INCLUDED
_DUMBOS_PROPS_MK_INCLUDED := true

# ==============================================================================
# DumbOS Low-RAM & Stability Properties
# All assignments use `?=` so device-specific props always win cleanly without
# duplicate-sysprop build errors (post_process_props.py).
# LMKD PSI is NEVER forced here (legacy kernels stay on native vmpressure).
# Standard LineageOS bootanimation is preserved.
# ==============================================================================

PRODUCT_SYSTEM_PROPERTIES += \
    ro.config.low_ram?=true \
    ro.config.avoid_gfx_accel?=true

PRODUCT_PRODUCT_PROPERTIES += \
    persist.traced.enable?=0 \
    ro.setupwizard.mode?=DISABLED \
    ro.launcher.blur.appLaunch?=0 \
    ro.surface_flinger.supports_background_blur?=0 \
    ro.config.max_starting_bg?=2 \
    pm.dexopt.downgrade_after_inactive_days?=10

endif # _DUMBOS_PROPS_MK_INCLUDED
