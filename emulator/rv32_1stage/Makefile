include ../../prefix.mk

MK_TARGET_PROC  ?= rv32_1stage
SUPERVISOR_MODE="false"

bmark_timeout ?=1000000

global_asm_tests += \

#	riscv-v3_lb \
	riscv-v3_lbu \
	riscv-v3_lh \
	riscv-v3_lhu \
	riscv-v3_sb \
	riscv-v3_sh \

include $(srcDir)/emulator/common/Makefile.include

