#=======================================================================
# UCB Chisel C++ Simulator Generator: Makefile 
#-----------------------------------------------------------------------
# Christopher Celio (celio@eecs.berkeley.edu)
#
# This makefile will generate a processor emulator from chisel code.
# 
# Many different processors are provided. To switch between which processor(s)
# you would like to build, simply set the $(targets) variable as appropriate. 

all_targets := rv32_1stage rv32_2stage rv32_3stage rv32_5stage rv32_ucode 
targets     := $(all_targets)
 
# To switch between which processor the Makefile would like to build, it will
# change the bash environment variable $MK_TARGET_PROC as follows:
#   "export MK_TARGET_PROC=rv32_1stage"
#   "export MK_TARGET_PROC=rv32_2stage"
#   "export MK_TARGET_PROC=rv32_5stage"
#   "export MK_TARGET_PROC=rv32_ucode"

RISCV           := /opt/riscv
srcDir          := $(abspath .)
buildIncludeDir := $(RISCV)/include
buildLibDir     := $(RISCV)/lib
buildDir        := $(abspath .)

# Paths to different source trees
chiseldir       := 

CXX := g++
SBT := java -Xmx4096M -Xss8M -XX:MaxPermSize=128M -jar $(srcDir)/sbt/sbt-launch.jar $(SBT_FLAGS)

MK_TARGET_PROC?=rv32_1stage

prerequisites := $(if $(chiseldir),chisel-timestamp)

all: $(prerequisites) $(patsubst %,emulator/%/emulator,$(targets))

install: all
	install -d $(RISCV)/bin
	$(if $(findstring rv32_1stage,$(targets)),install -s -p -m 755 emulator/rv32_1stage/emulator $(RISCV)/bin/rv32_1stage-emulator)
	$(if $(findstring rv32_2stage,$(targets)),install -s -p -m 755 emulator/rv32_2stage/emulator $(RISCV)/bin/rv32_2stage-emulator)
	$(if $(findstring rv32_3stage,$(targets)),install -s -p -m 755 emulator/rv32_3stage/emulator $(RISCV)/bin/rv32_3stage-emulator)
	$(if $(findstring rv32_5stage,$(targets)),install -s -p -m 755 emulator/rv32_5stage/emulator $(RISCV)/bin/rv32_5stage-emulator)
	$(if $(findstring rv32_ucode,$(targets)),install -s -p -m 755 emulator/rv32_ucode/emulator $(RISCV)/bin/rv32_ucode-emulator)

dist-src:
	cd $(srcDir) && git archive --prefix=sodor/ -o $(buildDir)/sodor-`git rev-parse --short HEAD`.tar.gz HEAD


compile:
	$(SBT) "project ${MK_TARGET_PROC}" compile

shell:
	$(SBT) "project ${MK_TARGET_PROC}" shell

debug:
	$(SBT) "project ${MK_TARGET_PROC}" "last run"

console:
	$(SBT) "project ${MK_TARGET_PROC}" console

run-emulator: $(patsubst %,emulator/%/generated-src/timestamp,$(targets))

run-emulator-debug: $(patsubst %,emulator/%/generated-src-debug/timestamp,$(targets))

clean-tests:
	make -C $(patsubst %,emulator/%,$(all_targets)) clean-tests

clean:
	-find sbt -type d -name target -exec rm -rf {} \+
	for d in $(patsubst %,emulator/%,$(all_targets)) ; do \
		make -C $$d clean ; \
	done
	$(RM) test-results.xml

reports: test-results.xml report-cpi report-bp report-stats

report-cpi: $(patsubst %,%-report-cpi,$(targets))

report-bp: $(patsubst %,%-report-bp,$(targets))

report-stats: $(patsubst %,%-report-stats,$(targets))

chisel-timestamp: $(wildcard $(chiseldir)/src/main/scala/*.scala)
	cd $(chiseldir) && $(SBT) publish-local
	date > $@

test-results.xml: $(wildcard $(patsubst %,emulator/%/output/*.out,$(targets)))
	$(srcDir)/project/check $^ > test-results.xml

%-report-cpi:
	-grep CPI emulator/$(patsubst %-report-cpi,%,$@)/output/*.out

%-report-bp:
	-grep Acc emulator/$(patsubst %-report-bp,%,$@)/output/*.out

%-report-stats:
	-grep "#" emulator/$(patsubst %-report-stats,%,$@)/output/*.out

emulator/%/generated-src/timestamp: emulator/%/emulator
	@echo
	@echo running basedir/Makefile: make run-emulator
	@echo
	make -C $(dir $<) run
	install -d $(dir $@)
	date > $@

emulator/%/generated-src-debug/timestamp: emulator/%/emulator-debug
	@echo
	@echo running basedir/Makefile: make run-emulator-debug
	@echo
	make -C $(dir $<) run-debug
	install -d $(dir $@)
	date > $@

emulator/%/emulator: $(prerequisites)
	make -C $(dir $@)

emulator/%/emulator-debug: $(prerequisites)
	make -C $(dir $@) emulator-debug

.PHONY: run-emulator target clean clean-tests

# Because we are using recursive makefiles and emulator is an actual file.
emulator/rv32_1stage/emulator: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_1stage/*.scala)

emulator/rv32_2stage/emulator: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_2stage/*.scala)

emulator/rv32_3stage/emulator: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_3stage/*.scala)

emulator/rv32_5stage/emulator: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_5stage/*.scala)

emulator/rv32_ucode/emulator: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_ucode/*.scala)

emulator/rv32_1stage/emulator-debug: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_1stage/*.scala)

emulator/rv32_2stage/emulator-debug: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_2stage/*.scala)

emulator/rv32_3stage/emulator-debug: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_3stage/*.scala)

emulator/rv32_5stage/emulator-debug: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_5stage/*.scala)

emulator/rv32_ucode/emulator-debug: $(wildcard $(srcDir)/src/common/*.scala) \
                                 $(wildcard $(srcDir)/src/rv32_ucode/*.scala)

.PHONY:	jenkins-build

jenkins-build:
	make clean run-emulator
	make reports
