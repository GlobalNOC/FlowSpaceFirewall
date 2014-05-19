Summary: Flowspace Firewall CLI 
Name: FSFW-CLI
Version: 1.0.0
Release: 2%{?dist}
License: CHECK
Group: Flowspace Firewall Utilities
URL: http://globalnoc.iu.edu/sdn/fsfw.html
Source0: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildArch: noarch
Requires: perl
Requires: perl-Term-ReadLine-Gnu
Requires: perl-GRNOC-Config
Requires: perl-GRNOC-WebService-Client




%description
FSFW CLI
%prep
%setup -q -n FSFW-CLI-%{version}

%build
%{__perl} Makefile.PL PREFIX="%{buildroot}%{_prefix}" INSTALLDIRS="vendor"
make

%install
rm -rf $RPM_BUILD_ROOT
make pure_install


%{__install} -d -m0755 %{buildroot}%{perl_sitelib}/FSFW/
%{__install} -d -p %{buildroot}/usr/bin/

%{__install} lib/FSFW/CLI.pm %{buildroot}%{perl_vendorlib}/FSFW/CLI.pm
%{__install} bin/fsfw-cli.pl %{buildroot}/usr/bin/fsfw-cli.pl



find %{buildroot} -name .packlist -exec %{__rm} {} \;

%{_fixperms} $RPM_BUILD_ROOT/*

%clean
rm -rf $RPM_BUILD_ROOT


%files
%defattr(-,root,root,-)
%{perl_vendorlib}/FSFW/CLI.pm

%defattr(0755,root,root,-)
/usr/bin/fsfw-cli.pl
%doc


%changelog
* Fri May  2 2014 Grant McNaught <gmcnaugh@nddi-dev.bldc.net.internet2.edu> - cli-1
- Initial build.

