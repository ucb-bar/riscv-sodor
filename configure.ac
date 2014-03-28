AC_INIT(sodor, 1.0)

AC_PROG_CXX

# Chisel/Scala sources
AC_ARG_WITH(chisel, [  --with-chisel           path to chisel sources])

AC_SUBST(chiseldir, $with_chisel)

AC_OUTPUT(
    prefix.mk
    Makefile
    project/plugins.sbt
    project/build.properties
    project/build.scala
    emulator/rv32_3stage/Makefile
    emulator/rv32_ucode/Makefile
)
