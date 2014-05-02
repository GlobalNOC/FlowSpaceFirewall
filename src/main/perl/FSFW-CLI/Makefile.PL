#!/usr/bin/perl

use strict;
use warnings;

use ExtUtils::MakeMaker;
use FindBin;

sub MY::postamble { <<"END"; }

rpm: manifest dist
	rpmbuild -ta FSFW-CLI-\$(VERSION).tar.gz

test_jenkins :
	make test PERL_TEST_HARNESS_DUMP_TAP=$FindBin::Bin/tap/

END

WriteMakefile(
              NAME                => 'FSFW::CLI',
              AUTHOR              => 'GRNOC Software Engineering',
              VERSION_FROM        => 'lib/FSFW/CLI.pm',
    PL_FILES            => {},
    PREREQ_PM => {
    },
    dist                => { COMPRESS => 'gzip -9f', SUFFIX => 'gz', },
    clean               => { FILES => 'FSFW-CLI*.gz' },
    );
