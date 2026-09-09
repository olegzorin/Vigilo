package dev.olegz.vf.api.account;

import dev.olegz.vf.registry.domain.account.Address;

public class ApiAddress {
    public String addrStreet1;
    public String addrStreet2;
    public String city;
    public String state;
    public String countryCode;
    public String postalCode;
    public String timezone;

    public ApiAddress() {
    }

    public ApiAddress(Address address) {
        if (address == null) return;

        this.addrStreet1 = address.addrStreet1;
        this.addrStreet2 = address.addrStreet2;
        this.city = address.city;
        this.state = address.state;
        this.countryCode = address.countryCode;
        this.postalCode = address.postalCode;
        this.timezone = address.timezone;
    }

    public Address toAddress() {
        Address address = new Address();
        address.addrStreet1 = addrStreet1;
        address.addrStreet2 = addrStreet2;
        address.city = city;
        address.state = state;
        address.countryCode = countryCode;
        address.postalCode = postalCode;
        address.timezone = timezone;
        return address;
    }
}
