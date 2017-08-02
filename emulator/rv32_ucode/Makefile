include ../../prefix.mk

MK_TARGET_PROC  ?= rv32_ucode

global_asm_tests += \
	rv32mi-p-csr \
	rv32mi-p-illegal \

bmark_timeout ?=10000000

include $(srcDir)/emulator/common/Makefile.include

