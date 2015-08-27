Name: flowspace-firewall
Version: 1.0.6
Release: 1%{?dist}
Summary: Flowspace Firewall
License: CHECK(Distributable)
Group: SDN
URL: http://globalnoc.iu.edu
Source0: flowspace-firewall.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)
BuildArch: noarch
Requires(pre): /usr/sbin/useradd, /usr/bin/getent
Requires(postun): /usr/sbin/userdel
Requires: java-1.6.0-sun

%description
Flowspace firewall application 

%prep
%setup -q -n %{name}

%pre
/usr/bin/getent passwd fsfw || /usr/sbin/useradd -r -d /var/lib/fsfw -s /bin/false fsfw

%postun


%build

%install
rm -rf $RPM_BUILD_ROOT
%{__install} -d -p %{buildroot}/usr/share/fsfw/
%{__install} -d -p %{buildroot}/etc/fsfw/
%{__install} -d -p %{buildroot}/etc/init.d/
%{__install} -d -p %{buildroot}/var/lib/floodlight/
%{__install} -d -p %{buildroot}/var/log/floodlight/
%{__install} -d -p %{buildroot}/var/run/fsfw

%{__install} jars/*.jar %{buildroot}//usr/share/fsfw/
%{__install} conf/*.xml %{buildroot}/etc/fsfw/
%{__install} conf/*.xsd %{buildroot}/etc/fsfw/
%{__install} conf/fsfw.init %{buildroot}/etc/init.d/fsfw

# clean up buildroot                                                                                                                                                                                                   
find %{buildroot} -name .packlist -exec %{__rm} {} \;

%{_fixperms} $RPM_BUILD_ROOT/*

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(755 ,fsfw, root, -)
/usr/share/fsfw/floodlight.jar
/usr/share/fsfw/flowspace_firewall.jar
/usr/share/fsfw/config_test.jar
/var/lib/floodlight
/var/run/fsfw

%defattr(755, root, root, -)
/etc/init.d/fsfw
%config(noreplace) /etc/fsfw/fsfw.xml
%config(noreplace) /etc/fsfw/logback.xml
%config /etc/fsfw/fsfw.xsd
