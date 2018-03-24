pipeline {
    agent { docker 'librecores/ci-osstools' }
    stages {
      stage("Test") {
        steps {
          	sh "git submodule update --init --recursive -- riscv-fesvr riscv-tests"
          	sh "cd riscv-fesvr; mkdir -p build; cd build; ../configure --prefix=/opt/riscv; make; cd ../../"
	        sh "git -C riscv-tests checkout master; git -C riscv-tests pull"
		sh "sed '/RISCV_GCC_OPTS/s/\$/ -march=rv32i -mabi=ilp32/' riscv-tests/isa/Makefile > temporary"
	        sh "mv temporary riscv-tests/isa/Makefile"
	        sh """make -C riscv-tests/isa rv32mi rv32ui-p-simple \
  rv32ui-p-add \
  rv32ui-p-addi \
  rv32ui-p-auipc \
  rv32ui-p-fence_i \
  rv32ui-p-sb \
  rv32ui-p-sh \
  rv32ui-p-sw \
  rv32ui-p-and \
  rv32ui-p-andi \
  rv32ui-p-beq \
  rv32ui-p-bge \
  rv32ui-p-bgeu \
  rv32ui-p-blt \
  rv32ui-p-bltu \
  rv32ui-p-bne \
  rv32ui-p-jal \
  rv32ui-p-jalr \
  rv32ui-p-lb \
  rv32ui-p-lbu \
  rv32ui-p-lh \
  rv32ui-p-lhu \
  rv32ui-p-lui \
  rv32ui-p-lw \
  rv32ui-p-or \
  rv32ui-p-ori \
  rv32ui-p-sll \
  rv32ui-p-slli \
  rv32ui-p-slt \
  rv32ui-p-slti \
  rv32ui-p-sra \
  rv32ui-p-srai \
  rv32ui-p-sub \
  rv32ui-p-xor \
  rv32ui-p-xori """
	        sh "sed '/RISCV_GCC_OPTS/s/\$/ -march=rv32i -mabi=ilp32/' riscv-tests/benchmarks/Makefile > temporary"
          	sh "mv temporary riscv-tests/benchmarks/Makefile"
	        sh "make -C riscv-tests/benchmarks dhrystone.riscv median.riscv multiply.riscv qsort.riscv rsort.riscv towers.riscv vvadd.riscv"
          	sh "make run-emulator"
        }
      }
    }
}
