DESCRIPTION = "lxc aims to use these new functionnalities to provide an userspace container object"
SECTION = "console/utils"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=4fbd65380cdd255951079008b364516c"
DEPENDS = "libxml2 libcap"
RDEPENDS_${PN} = " \
		rsync \
		gzip \
		xz \
		tar \
		libcap-bin \
		bridge-utils \
		dnsmasq \
		perl-module-strict \
		perl-module-getopt-long \
		perl-module-vars \
		perl-module-warnings-register \
		perl-module-exporter \
		perl-module-constant \
		perl-module-overload \
		perl-module-exporter-heavy \
		gmp \
		libidn \
		gnutls \
		nettle \
		util-linux-mountpoint \
"

RDEPENDS_${PN}_append_libc-glibc = " glibc-utils"

RDEPENDS_${PN}-ptest += "file make gmp nettle gnutls bash libgcc"

SRC_URI = "http://linuxcontainers.org/downloads/${BPN}-${PV}.tar.gz \
	file://lxc-1.0.0-disable-udhcp-from-busybox-template.patch \
	file://run-ptest \
	file://lxc-fix-B-S.patch \
	file://lxc-doc-upgrade-to-use-docbook-3.1-DTD.patch \
	file://logs-optionally-use-base-filenames-to-report-src-fil.patch \
	file://cgroups-work-around-issue-in-gcc-7.patch \
	file://templates-actually-create-DOWNLOAD_TEMP-directory.patch \
	file://template-make-busybox-template-compatible-with-core-.patch \
	file://tests-our-init-is-not-busybox.patch \
	file://tests-add-no-validate-when-using-download-template.patch \
	file://dnsmasq.conf \
	"

SRC_URI[md5sum] = "7bfd95280522d7936c0979dfea92cdb5"
SRC_URI[sha256sum] = "0d8e34b302cfe4c40c6c9ae5097096aa5cc2c1dfceea3f0f22e3e16c4a4e8494"

S = "${WORKDIR}/${BPN}-${PV}"

# Let's not configure for the host distro.
#
PTEST_CONF = "${@bb.utils.contains('DISTRO_FEATURES', 'ptest', '--enable-tests', '', d)}"
EXTRA_OECONF += "--with-distro=${DISTRO} ${PTEST_CONF}"

EXTRA_OECONF += "--with-init-script=\
${@bb.utils.contains('DISTRO_FEATURES', 'sysvinit', 'sysvinit,', '', d)}\
${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'systemd', '', d)}"

EXTRA_OECONF += "--enable-log-src-basename"

CFLAGS_append = " -Wno-error=deprecated-declarations"

PACKAGECONFIG ??= "templates \
    ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'systemd', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'selinux', 'selinux', '', d)} \
"
PACKAGECONFIG[doc] = "--enable-doc --enable-api-docs,--disable-doc --disable-api-docs,,"
PACKAGECONFIG[rpath] = "--enable-rpath,--disable-rpath,,"
PACKAGECONFIG[apparmor] = "--enable-apparmor,--disable-apparmor,apparmor,apparmor"
PACKAGECONFIG[templates] = ",,, ${PN}-templates"
PACKAGECONFIG[selinux] = "--enable-selinux,--disable-selinux,libselinux,libselinux"
PACKAGECONFIG[seccomp] ="--enable-seccomp,--disable-seccomp,libseccomp,libseccomp"
PACKAGECONFIG[systemd] = "--with-systemdsystemunitdir=${systemd_unitdir}/system/,--without-systemdsystemunitdir,systemd,"
PACKAGECONFIG[python] = "--enable-python,--disable-python,python3,python3-core"
PACKAGECONFIG[lua] = "--enable-lua,--disable-lua,lua,lua"

# required by python3 to run setup.py
export BUILD_SYS
export HOST_SYS
export STAGING_INCDIR
export STAGING_LIBDIR

inherit autotools pkgconfig ptest update-rc.d systemd python3native

SYSTEMD_PACKAGES = "${PN}-setup"
SYSTEMD_SERVICE_${PN}-setup = "lxc.service"
SYSTEMD_AUTO_ENABLE_${PN}-setup = "disable"

INITSCRIPT_PACKAGES = "${PN}-setup"
INITSCRIPT_NAME_{PN}-setup = "lxc"
INITSCRIPT_PARAMS_${PN}-setup = "defaults"

FILES_${PN}-doc = "${mandir} ${infodir}"
# For LXC the docdir only contains example configuration files and should be included in the lxc package
FILES_${PN} += "${docdir}"
FILES_${PN} += "${libdir}/python3*"
FILES_${PN} += "${datadir}/bash-completion"
FILES_${PN}-dbg += "${libexecdir}/lxc/.debug"
FILES_${PN}-dbg += "${libexecdir}/lxc/hooks/.debug"
PACKAGES =+ "${PN}-templates ${PN}-setup ${PN}-networking ${PN}-lua"
FILES_lua-${PN} = "${datadir}/lua ${libdir}/lua"
FILES_lua-${PN}-dbg += "${libdir}/lua/lxc/.debug"
FILES_${PN}-templates += "${datadir}/lxc/templates"
RDEPENDS_${PN}-templates += "bash"

ALLOW_EMPTY_${PN}-networking = "1"

FILES_${PN}-setup += "/etc/tmpfiles.d"
FILES_${PN}-setup += "/lib/systemd/system"
FILES_${PN}-setup += "/usr/lib/systemd/system"
FILES_${PN}-setup += "/etc/init.d"

CACHED_CONFIGUREVARS += " \
    ac_cv_path_PYTHON='${STAGING_BINDIR_NATIVE}/python3-native/python3' \
    am_cv_python_pyexecdir='${exec_prefix}/${libdir}/python3.5/site-packages' \
    am_cv_python_pythondir='${prefix}/${libdir}/python3.5/site-packages' \
"

do_install_append() {
	# The /var/cache/lxc directory created by the Makefile
	# is wiped out in volatile, we need to create this at boot.
	rm -rf ${D}${localstatedir}/cache
	install -d ${D}${sysconfdir}/default/volatiles
	echo "d root root 0755 ${localstatedir}/cache/lxc none" \
	     > ${D}${sysconfdir}/default/volatiles/99_lxc

	for i in `grep -l "#! */bin/bash" ${D}${datadir}/lxc/hooks/*`; do \
	    sed -e 's|#! */bin/bash|#!/bin/sh|' -i $i; done

	if ${@bb.utils.contains('DISTRO_FEATURES', 'sysvinit', 'true', 'false', d)}; then
	    install -d ${D}${sysconfdir}/init.d
	    install -m 755 config/init/sysvinit/lxc* ${D}${sysconfdir}/init.d
	fi

	# since python3-native is used for install location this will not be
	# suitable for the target and we will have to correct the package install
	if ${@bb.utils.contains('PACKAGECONFIG', 'python', 'true', 'false', d)}; then
	    if [ -d ${D}${exec_prefix}/lib/python* ]; then mv ${D}${exec_prefix}/lib/python* ${D}${libdir}/; fi
	    rmdir --ignore-fail-on-non-empty ${D}${exec_prefix}/lib
	fi

	# Force the main dnsmasq instance to bind only to specified interfaces and
	# to not bind to virbr0. Libvirt will run its own instance on this interface.
	install -d ${D}/${sysconfdir}/dnsmasq.d
	install -m 644 ${WORKDIR}/dnsmasq.conf ${D}/${sysconfdir}/dnsmasq.d/lxc
}

EXTRA_OEMAKE += "TEST_DIR=${D}${PTEST_PATH}/src/tests"

do_install_ptest() {
	# Move tests to the "ptest directory"
	install -d ${D}/${PTEST_PATH}/tests
	mv ${D}/usr/bin/lxc-test-* ${D}/${PTEST_PATH}/tests/.
}

pkg_postinst_${PN}() {
	if [ -z "$D" ] && [ -e /etc/init.d/populate-volatile.sh ] ; then
		/etc/init.d/populate-volatile.sh update
	fi
}

pkg_postinst_${PN}-networking() {
	if [ "x$D" != "x" ]; then
		exit 1
	fi

	# setup for our bridge
        echo "lxc.network.link=lxcbr0" >> ${sysconfdir}/lxc/default.conf

cat >> /etc/network/interfaces << EOF

auto lxcbr0
iface lxcbr0 inet dhcp
	bridge_ports eth0
	bridge_fd 0
	bridge_maxwait 0
EOF

cat<<EOF>/etc/network/if-pre-up.d/lxcbr0
#! /bin/sh

if test "x\$IFACE" = xlxcbr0 ; then
        brctl show |grep lxcbr0 > /dev/null 2>/dev/null
        if [ \$? != 0 ] ; then
                brctl addbr lxcbr0
                brctl addif lxcbr0 eth0
                ip addr flush eth0
                ifconfig eth0 up
        fi
fi
EOF
chmod 755 /etc/network/if-pre-up.d/lxcbr0
}
