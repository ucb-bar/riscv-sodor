SHELL := /bin/sh

CC := riscv32-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP := riscv32-unknown-elf-objdump

CFLAGS := -O2 -g -Wall -std=gnu99 -mcmodel=medany -fno-common -fno-builtin-printf \
	-I ../env/p -I ../env/
LDFLAGS := -static -nostdlib -nostartfiles -T ../env/p/link.ld

programs := movn yourinst
bins := $(addprefix rv32ui-p-,$(programs))
dumps := $(addsuffix .dump,$(bins))

all: $(bins)
dump: $(dumps)

rv32ui-p-%: %.S
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $<

%.dump: %
	$(OBJDUMP) -d $< > $@

clean:
	rm -f -- $(bins) $(dumps)

.PHONY: all dump clean
.SUFFIXES:
