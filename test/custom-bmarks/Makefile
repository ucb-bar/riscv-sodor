CC=riscv64-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP=riscv64-unknown-elf-objdump
CFLAGS=-mcmodel=medany -std=gnu99 -O0 -fno-common -fno-builtin-printf -Wall
LDFLAGS=-static -nostdlib -nostartfiles -lgcc

PROGRAMS=mix
EXECUTABLES=$(addsuffix .riscv,$(PROGRAMS))
DUMPS=$(addsuffix .dump,$(PROGRAMS))
OUTFILES=$(addsuffix .out,$(PROGRAMS))

all: $(EXECUTABLES)

run-riscv: $(OUTFILES)

dump: $(DUMPS)

%.out: %.riscv
	spike --isa=rv32i -l $< &> $@

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

%.riscv: %.o crt.o link.ld
	$(CC) -T link.ld $(LDFLAGS) $< crt.o -o $@

%.o: %.S
	$(CC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f *.o *.riscv *.dump *.out
