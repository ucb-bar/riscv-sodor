CC=riscv64-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP=riscv64-unknown-elf-objdump
CFLAGS=-mcmodel=medany -std=gnu99 -O2 -fno-common -fno-builtin-printf -Wall
LDFLAGS=-static -nostdlib -nostartfiles -lgcc
RVTESTS=$(abspath ../../riscv-tests)
INCLUDE=-I$(RVTESTS)/env/p -I$(RVTESTS)/isa/macros/scalar

PROGRAMS=custom
EXECUTABLES=$(addsuffix .riscv,$(PROGRAMS))
DUMPS=$(addsuffix .dump,$(PROGRAMS))

all: $(EXECUTABLES)

dump: $(DUMPS)

%.dump: %
	$(OBJDUMP) -D $< > $@

%.o: %.S
	$(CC) $(CFLAGS) $(INCLUDE) -c $< -o $@

%.riscv: %.o
	$(CC) $(LDFLAGS) -T $(RVTESTS)/env/p/link.ld $< -o $@

clean:
	rm -f *.riscv *.o
