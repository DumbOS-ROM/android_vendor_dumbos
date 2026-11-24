# Device Kernel Recommendations (Optional)

**DumbOS does not patch, override, or replace any device's kernel.**  
Each phone continues running its own proven device-tree kernel cleanly out of the box (no forced `ro.lmk.use_psi=true` or invasive kernel flags).

If you are a device maintainer tuning a kernel `defconfig` specifically for **ultra-low RAM (1–2 GB) dumbphone performance**, here are optional best-practice recommendations:

1. **Disable Heavy Debug Overhead**
   - Ensure `# CONFIG_SLUB_DEBUG is not set`, `# CONFIG_DEBUG_KERNEL is not set`, and `# CONFIG_SCHED_DEBUG is not set` in production defconfigs. Leaving `CONFIG_SLUB_DEBUG=y` enabled on dual-core CPUs degrades allocation throughput severely.
2. **ZRAM Compression (LZ4)**
   - Enable `CONFIG_ZRAM=y`, `CONFIG_CRYPTO_LZ4=y`, `CONFIG_ZRAM_DEF_COMP_LZ4=y` (`LZ4` cuts CPU stall compared to LZO/deflate when swapping).
3. **Memory Pressure Handling (`PSI` vs Legacy `vmpressure`)**
   - **Linux 4.14+**: `CONFIG_PSI=y` works natively with AOSP LMKD.
   - **Legacy Linux 3.4 / 3.10 / 3.18**: Keep PSI disabled cleanly; DumbOS intentionally leaves `ro.lmk.use_psi` unset so LMKD uses native `vmpressure` without `kswapd0` thrashing.
4. **Legacy Kernel AOSP 13/14/15 Backports**
   - `CONFIG_MEMFD_CREATE` / `ashmem` compat + `CONFIG_BPF_SYSCALL` / cgroup v2 backports required by your target LineageOS / LineageOS-UL Android version.
