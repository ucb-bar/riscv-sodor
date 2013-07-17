AC_INIT(sodor, 1.0)

AC_PROG_CC

# Chisel/Scala sources
AC_ARG_WITH(chisel, [  --with-chisel           path to chisel sources])

AC_SUBST(chiseldir, $with_chisel)

AC_OUTPUT(
    prefix.mk
    Makefile
    project/build.properties
    project/build.scala
    emulator/rv32_1stage/Makefile
    emulator/rv32_2stage/Makefile
    emulator/rv32_3stage/Makefile
    emulator/rv32_5stage/Makefile
    emulator/rv32_ucode/Makefile
)
