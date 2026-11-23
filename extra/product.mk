# Auto-hook entry point invoked by LineageOS vendor/lineage/config/common.mk line 2:
#   $(call inherit-product-if-exists, vendor/extra/product.mk)
# Manifest <linkfile src="extra/product.mk" dest="vendor/extra/product.mk"/> activates
# DumbOS automatically for any device tree without editing BoardConfig.mk or device makefiles.
$(call inherit-product-if-exists, vendor/dumbos/config/common.mk)
