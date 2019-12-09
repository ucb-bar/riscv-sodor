AC_INIT(sodor, 1.0)

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
)
