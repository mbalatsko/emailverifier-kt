set -ex

RESOURCES_DIR="./src/main/resources/offline-data"

# Disposable email providers
curl --output "$RESOURCES_DIR/disposable.txt" "https://raw.githubusercontent.com/disposable/disposable-email-domains/master/domains_strict.txt"

# Free email providers
curl --output "$RESOURCES_DIR/free.txt" "https://gist.githubusercontent.com/okutbay/5b4974b70673dfdcc21c517632c1f984/raw/daa988474b832059612f1b2468fba6cfcd2390dd/free_email_provider_domains.txt"

# Mozilla PSL
curl --output "$RESOURCES_DIR/psl.txt" "https://publicsuffix.org/list/public_suffix_list.dat"

# Role-based usernames
curl --output "$RESOURCES_DIR/role-based.txt" "https://raw.githubusercontent.com/mbalatsko/role-based-email-addresses-list/main/list.txt"
