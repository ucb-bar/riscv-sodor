AC_INIT(sodor, 1.0)

if [[ "$RISCV" == "" ]]
then
  echo 'The $RISCV environment variable must be set!'
  exit 1
fi

AC_PROG_CXX

# Chisel/Scala sources
AC_ARG_WITH(chisel, [  --with-chisel           path to chisel sources])

AS_IF([test "x$with_chisel" != "x"],
      [AC_SUBST(chisel_version, latest.SNAPSHOT)],
      [AC_SUBST(chisel_version, latest.release)])

AC_SUBST(chiseldir, $with_chisel)

AC_OUTPUT(
    prefix.mk
    Makefile
    project/plugins.sbt
    project/build.properties
    project/build.scala
)
