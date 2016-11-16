package com.sap.cloud.lm.sl.cf.core.dao;

import static com.sap.cloud.lm.sl.cf.core.model.ConfigurationFilter.CONTENT_FILTER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cloud.lm.sl.cf.core.dto.ConfigurationEntryDto;
import com.sap.cloud.lm.sl.cf.core.dto.ConfigurationEntryDto.FieldNames;
import com.sap.cloud.lm.sl.cf.core.message.Messages;
import com.sap.cloud.lm.sl.cf.core.model.PersistenceMetadata;
import com.sap.cloud.lm.sl.cf.core.model.PersistenceMetadata.NamedQueries;
import com.sap.cloud.lm.sl.common.ConflictException;
import com.sap.cloud.lm.sl.common.NotFoundException;
import com.sap.cloud.lm.sl.common.util.CommonUtil;

@Component
public class ConfigurationEntryDtoDao {

    @Autowired
    @Qualifier("configurationEntryEntityManagerFactory")
    EntityManagerFactory emf;

    public ConfigurationEntryDto add(ConfigurationEntryDto entry) throws ConflictException {
        try {
            return new TransactionalExecutor<ConfigurationEntryDto>(createEntityManager()).execute((manager) -> {

                manager.persist(entry);
                return entry;

            });
        } catch (RollbackException e) {
            throw new ConflictException(e, Messages.CONFIGURATION_ENTRY_ALREADY_EXISTS, entry.getProviderNid(), entry.getProviderId(),
                entry.getProviderVersion(), entry.getTargetSpace());
        }
    }

    public ConfigurationEntryDto update(long id, ConfigurationEntryDto entryDelta) throws NotFoundException, ConflictException {
        try {
            return new TransactionalExecutor<ConfigurationEntryDto>(createEntityManager()).execute((manager) -> {

                ConfigurationEntryDto existingEntry = findInternal(id, manager);
                if (existingEntry == null) {
                    throw new NotFoundException(Messages.CONFIGURATION_ENTRY_NOT_FOUND, id);
                }
                ConfigurationEntryDto entry = merge(existingEntry, entryDelta);
                manager.merge(entry);
                return entry;

            });
        } catch (RollbackException e) {
            ConfigurationEntryDto entry = merge(find(id), entryDelta);
            throw new ConflictException(e, Messages.CONFIGURATION_ENTRY_ALREADY_EXISTS, entry.getProviderNid(), entry.getProviderId(),
                entry.getProviderVersion(), entry.getTargetSpace());
        }
    }

    public void remove(long id) throws NotFoundException {
        new TransactionalExecutor<Void>(createEntityManager()).execute((manager) -> {

            ConfigurationEntryDto entry = findInternal(id, manager);
            if (entry == null) {
                throw new NotFoundException(Messages.CONFIGURATION_ENTRY_NOT_FOUND, id);
            }
            manager.remove(entry);
            return null;

        });
    }

    @SuppressWarnings("unchecked")
    public List<ConfigurationEntryDto> findAll() {
        return new Executor<List<ConfigurationEntryDto>>(createEntityManager()).execute((manager) -> {

            return manager.createNamedQuery(NamedQueries.FIND_ALL_ENTRIES).getResultList();

        });
    }

    public List<ConfigurationEntryDto> find(String providerNid, String providerId, String targetSpace,
        Map<String, String> requiredProperties, String mtaId) {
        return new Executor<List<ConfigurationEntryDto>>(createEntityManager()).execute((manager) -> {

            return findInternal(providerNid, providerId, targetSpace, requiredProperties, mtaId, manager);

        });
    }

    private List<ConfigurationEntryDto> findInternal(String providerNid, String providerId, String targetSpace,
        Map<String, String> requiredProperties, String mtaId, EntityManager manager) {

        TypedQuery<ConfigurationEntryDto> query = createQuery(providerNid, providerId, targetSpace, mtaId, manager);

        return filter(query.getResultList(), requiredProperties);

    }

    public ConfigurationEntryDto find(long id) throws NotFoundException {
        return new Executor<ConfigurationEntryDto>(createEntityManager()).execute((manager) -> {

            ConfigurationEntryDto entry = findInternal(id, manager);
            if (entry == null) {
                throw new NotFoundException(Messages.CONFIGURATION_ENTRY_NOT_FOUND, id);
            }
            return entry;

        });
    }

    public boolean exists(long id) {
        return new Executor<Boolean>(createEntityManager()).execute((manager) -> {

            return findInternal(id, manager) != null;

        });
    }

    private TypedQuery<ConfigurationEntryDto> createQuery(String providerNid, String providerId, String targetSpace, String mtaId,
        EntityManager manager) {
        CriteriaBuilder builder = manager.getCriteriaBuilder();
        CriteriaQuery<ConfigurationEntryDto> query = builder.createQuery(ConfigurationEntryDto.class);
        Root<ConfigurationEntryDto> root = query.from(ConfigurationEntryDto.class);

        List<Predicate> predicates = new ArrayList<>();
        if (providerNid != null) {
            predicates.add(builder.equal(root.get(FieldNames.PROVIDER_NID), providerNid));
        }
        if (targetSpace != null) {
            predicates.add(builder.equal(root.get(FieldNames.TARGET_SPACE), targetSpace));
        }
        if (providerId != null) {
            predicates.add(builder.equal(root.get(FieldNames.PROVIDER_ID), providerId));
        } else if (mtaId != null) {
            predicates.add(builder.like(root.get(FieldNames.PROVIDER_ID), mtaId + ":%"));
        }

        return manager.createQuery(query.select(root).where(predicates.toArray(new Predicate[0])));
    }

    private ConfigurationEntryDto findInternal(long id, EntityManager manager) {
        return manager.find(ConfigurationEntryDto.class, id);
    }

    private EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

    private List<ConfigurationEntryDto> filter(List<ConfigurationEntryDto> entries, Map<String, String> requiredProperties) {
        return entries.stream().filter((entry) -> CONTENT_FILTER.apply(entry.getContent(), requiredProperties)).collect(
            Collectors.toList());
    }

    private ConfigurationEntryDto merge(ConfigurationEntryDto existingEntry, ConfigurationEntryDto entry) {
        long id = existingEntry.getId();
        String providerNid = CommonUtil.merge(existingEntry.getProviderNid(), removeDefault(entry.getProviderNid()), null);
        String providerId = CommonUtil.merge(existingEntry.getProviderId(), entry.getProviderId(), null);
        String targetSpace = CommonUtil.merge(existingEntry.getTargetSpace(), entry.getTargetSpace(), null);
        String providerVersion = CommonUtil.merge(existingEntry.getProviderVersion(), removeDefault(entry.getProviderVersion()), null);
        String cntent = CommonUtil.merge(existingEntry.getContent(), entry.getContent(), null);
        return new ConfigurationEntryDto(id, providerNid, providerId, providerVersion, targetSpace, cntent);
    }

    private String removeDefault(String value) {
        return value.equals(PersistenceMetadata.NOT_AVAILABLE) ? null : value;
    }

}
