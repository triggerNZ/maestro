#!/bin/bash
# maestro vagrant bootstrap script
#
#  Initialise enough of an environment to install puppet and complete the
#  configuration of a basic maestro development environment, but don't clobber
#  any existing configuration, since this script may be called again by the
#  vagrant provisioner.
#
#  Before we can run puppet, we need to:
#    1. Install any required certificates.
#    2. Test and configure the proxy, if required.
#    3. Download and install the puppet repository metapackage.
#    4. Update the apt package list.
#    5. Install puppet.
# 

PROXY_ENABLE="yes"
PROXY_HOST="192.168.42.1"
PROXY_PORT="3128"
PROXY="$PROXY_HOST:$PROXY_PORT"
PROXY_HTTP="http://$PROXY"
PROXY_HTTPS="https://$PROXY"
PROXY_APT="/etc/apt/apt.conf.d/120proxy"
PROXY_PROFILE="/etc/profile.d/10proxy.sh"

PUPPET_PKG_URL="https://apt.puppetlabs.com/puppetlabs-release-wheezy.deb"
PUPPET_PKG_NAME="puppetlabs-release-wheezy.deb"

ECHO="echo [maestro-init]"


#
# Certificates
#

$ECHO Installing certificates...

N_CERTS=$(ls /vagrant/certs/ | grep -v README | wc -l)
if [ $N_CERTS == "0" ]
then
    $ECHO "...no certificates to install"
else
    cp -r /vagrant/certs /usr/local/share/ca-certificates/maestro
    for i in /usr/local/share/ca-certificates/maestro/*.pem
    do
        if [ -r $i ]
        then
            mv $i $i.crt
        fi
    done
    
    update-ca-certificates > /dev/null
    E=$?
    if [ $E -eq 0 ]
    then
        $ECHO "...installed $N_CERTS certificate(s)".
    else
        $ECHO "...FAILED ($E)".
        exit $E
    fi
fi


#
# Proxy
#

if [ $PROXY_ENABLE == "yes" ]
then
    $ECHO Configuring proxy:
    
    $ECHO "  Testing connectivity..."
    exec 6<>/dev/tcp/$PROXY_HOST/$PROXY_PORT
    E=$?
    if [ $E -eq 0 ]
    then
        $ECHO "  ...connection established to $PROXY."
        # Always remember to clean up :)
        exec 6>&-
        exec 6<&-
    else
        $ECHO "  ...FAILED to establish a connection to $PROXY ($E)."
        $ECHO "Unable to connect to proxy server. Possible causes:"
        $ECHO "  - Forgot to add 'Gateway yes' to /usr/local/etc/cntlm.conf"
        $ECHO "  - Forgot to restart cntlm after modifying cntlm.conf"
        $ECHO "  - Changed network settings in Vagrantfile but not bootstrap.sh"
        exit $E
    fi
    
    $ECHO "  Configuring system profile..."
    if [ ! -f $PROXY_PROFILE ]
    then
        touch $PROXY_PROFILE
        echo export http_proxy=$PROXY_HTTP >> $PROXY_PROFILE
        echo export https_proxy=$PROXY_HTTPS >> $PROXY_PROFILE
        $ECHO "  ...created $PROXY_PROFILE."
    else
        $ECHO "  ...file $PROXY_PROFILE exists, skipping."
    fi
    
    $ECHO "  Configuring apt..."
    if [ ! -f $PROXY_APT ]
    then
        touch $PROXY_APT
        echo Acquire::http::Proxy \"$PROXY_HTTP\"\; >> $PROXY_APT
        echo Acquire::https::Proxy \"$PROXY_HTTPS\"\; >> $PROXY_APT
        $ECHO "  ...created $PROXY_APT."
    else
        $ECHO "  ...file $PROXY_APT exists, skipping."
    fi
    
    . $PROXY_PROFILE # We'll need this for wget in the next step
fi


#
# Puppet
#

$ECHO Configuring puppet:

$ECHO "  Downloading repository metapackage..."
wget --quiet -P /tmp $PUPPET_PKG_URL
E=$?
if [ $E -eq 0 ]
then
    $ECHO "  ...saved to /tmp/$PUPPET_PKG_NAME."
else
    $ECHO "  ...FAILED ($E)."
    exit $E
fi

$ECHO "  Installing repository metapackage..."
dpkg -i /tmp/$PUPPET_PKG_NAME > /dev/null
E=$?
if [ $E -eq 0 ]
then
    $ECHO "  ...installed."
else
    $ECHO "  ...FAILED ($E)."
    exit $E
fi

$ECHO "  Updating package lists..."
apt-get -qq update
E=$?
if [ $E -eq 0 ]
then
    $ECHO "  ...updated."
else
    $ECHO "  ...FAILED ($E)."
    exit $E
fi

$ECHO "  Installing puppet... "
apt-get -y -qq install puppet > /dev/null
E=$?
if [ $E -eq 0 ]
then
    $ECHO "  ...installed."
else
    $ECHO "  ...FAILED ($E)."
    exit $E
fi

