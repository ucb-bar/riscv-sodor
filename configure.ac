AC_INIT(sodor, 1.0)

AC_PROG_CXX

# The RISCV toolchain needs to be passed in somehow.  You can pass it
# in two ways, with "--with-riscv" (like a normal autoconf option), or
# by using the RISCV environment variable (like the rest of the
# tools).
AC_ARG_WITH(riscv,  [  --with-riscv            path to RISCV toolchain])

AC_ARG_VAR(RISCV,   [path to RISCV toolchain])
AS_IF([test "x$with_riscv" != "x"],
      [AC_SUBST(riscv_path, $with_riscv)
       CXXFLAGS="$CXXFLAGS -I$with_riscv/include"
       CPPFLAGS="$CPPFLAGS -I$with_riscv/include"],
      [AS_IF([test "x$RISCV" != "x"],
             [CXXFLAGS="$CXXFLAGS -I$RISCV/include"
              CPPFLAGS="$CPPFLAGS -I$RISCV/include"
              AC_SUBST(riscv_path, "$RISCV")
              echo "Using RISCV enviornment variable tools at $RISCV"],
             [CXXFLAGS="$CXXFLAGS -I$prefix/include"
              CPPFLAGS="$CPPFLAGS -I$prefix/include"
              AC_SUBST(riscv_path, $prefix)])])

AC_LANG_PUSH([C++])
CXXFLAGS="$CXXFLAGS -std=gnu++0x"
AC_CHECK_HEADERS([fesvr/htif_pthread.h],
                 [found_fesvr_headers=yes; break;])

AS_IF([test "x$found_fesvr_headers" != "xyes"],
      [AC_MSG_ERROR([Unable to find RISCV fesvr headers, try --with-riscv])])
AC_LANG_POP

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
