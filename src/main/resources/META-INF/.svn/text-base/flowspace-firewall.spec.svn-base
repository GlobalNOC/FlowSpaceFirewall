Name: flowspace-firewall
Version: 1.0.1
Release: 1
Summary: Flowspace Firewall
License: CHECK(Distributable)
Group: SDN
URL: http://globalnoc.iu.edu
Source0: flowspace-firewall.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)
BuildArch: noarch
Requires(pre): /usr/sbin/useradd, /usr/bin/getent
Requires(postun): /usr/sbin/userdel
Requires: java-1.6.0-ibm

%description
Flowspace firewall application 

%prep
%setup -q -n %{name}

%pre
/usr/bin/getent passwd fsf || /usr/sbin/useradd -r -d /var/lib/fsf -s /bin/false fsf

%postun
/usr/sbin/userdel fsf

%build

%install
rm -rf $RPM_BUILD_ROOT
%{__install} -d -p %{buildroot}/usr/share/fsf/
%{__install} -d -p %{buildroot}/etc/fsf/
%{__install} -d -p %{buildroot}/etc/init.d/
%{__install} -d -p %{buildroot}/var/lib/floodlight/
%{__install} -d -p %{buildroot}/var/log/floodlight/

%{__install} jars/*.jar %{buildroot}//usr/share/fsf/
%{__install} conf/*.xml %{buildroot}/etc/fsf/
%{__install} conf/fsf.init %{buildroot}/etc/init.d/fsf

# clean up buildroot                                                                                                                                                                                                   
find %{buildroot} -name .packlist -exec %{__rm} {} \;

%{_fixperms} $RPM_BUILD_ROOT/*

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(755 ,fsf, root, -)
/usr/share/fsf/floodlight.jar
/usr/share/fsf/flowspace_firewall.jar
/var/lib/floodlight

%defattr(755, root, root, -)
/etc/init.d/fsf
%config(noreplace) /etc/fsf/fsf.xml
%config(noreplace) /etc/fsf/logback.xml
