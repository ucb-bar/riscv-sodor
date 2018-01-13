CC=riscv64-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP=riscv64-unknown-elf-objdump
CFLAGS=-mcmodel=medany -std=gnu99 -O2 -fno-common -fno-builtin-printf -Wall
LDFLAGS=-static -nostdlib -nostartfiles -lgcc
RVTESTS=$(abspath ../../riscv-tests)
INCLUDE=-I$(RVTESTS)/env/p -I$(RVTESTS)/isa/macros/scalar

PROGRAMS=rv32ui-p-yourinst rv32ui-p-movn
DUMPS=$(addsuffix .dump,$(PROGRAMS))

all: $(PROGRAMS)

dump: $(DUMPS)

%.dump: %
	$(OBJDUMP) -D $< > $@

%: %.o
	$(CC) $(LDFLAGS) -T $(RVTESTS)/env/p/link.ld $< -o $@

rv32ui-p-%.o: %.S
	$(CC) $(CFLAGS) $(INCLUDE) -c $< -o $@

clean:
	rm -f $(PROGRAMS) *.dump *.o
