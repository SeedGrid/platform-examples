package br.com.seedgrid.auditexample.services;

import java.util.List;

import br.com.seedgrid.auditexample.entities.Customer;
import br.com.seedgrid.auditexample.repositories.CustomerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> listAll() {
        return customerRepository.listAll();
    }

    public Customer findByPublicId(String publicId) {
        return customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + publicId));
    }

    @Transactional
    public Customer create(Customer customer) {
        customerRepository.persist(customer);
        return customer;
    }

    @Transactional
    public Customer update(String publicId, Customer input) {
        Customer customer = findByPublicId(publicId);
        customer.cpf = input.cpf;
        customer.name = input.name;
        customer.occupation = input.occupation;
        customer.job = input.job;
        customer.birthDate = input.birthDate;
        customer.address = input.address;
        customer.postalCode = input.postalCode;
        customer.city = input.city;
        customer.comment = input.comment;
        customer.internalCode = input.internalCode;
        return customer;
    }

    @Transactional
    public void delete(String publicId) {
        Customer customer = findByPublicId(publicId);
        customerRepository.delete(customer);
    }
}
