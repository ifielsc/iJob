// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.notify.MailChannelPageQuery;
import com.ifielsc.ijob.core.notify.security.NotifyConfigCipher;
import com.ifielsc.ijob.infra.entity.notify.MailChannel;
import com.ifielsc.ijob.infra.mapper.notify.MailChannelMapper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class MailChannelDeletionTest {
    private final Store store = new Store();
    private final NotifyConfigCipher cipher = cipher();
    private final MailChannelService service =
            new MailChannelService(store, new ObjectMapper(), new AppProperties(), cipher);

    @Test
    void deletesUnreferencedChannelsAfterSortedLocks() {
        service.deleteBatch(batch(2L, 1L));
        assertEquals(List.of(1L, 2L), store.locks);
        assertTrue(store.ids.isEmpty());
    }

    @Test
    void rejectsReferencesIncludingDisabledConfigurationsAndPendingSnapshots() {
        for (List<String> references : List.of(store.configs, store.pending)) {
            references.add(cipher.encrypt("{\"mailChannelId\":2}"));
            BusinessException error =
                    assertThrows(BusinessException.class, () -> service.deleteBatch(batch(1L, 2L)));
            assertEquals("IJOB-DETAIL-0208", error.getErrorCode());
            assertEquals(Set.of(1L, 2L), store.ids);
            references.clear();
        }
    }

    @Test
    void missingOrDuplicateTargetsNeverDeleteOtherChannels() {
        assertThrows(BusinessException.class, () -> service.deleteBatch(batch(1L, 99L)));
        assertThrows(BusinessException.class, () -> service.deleteBatch(batch(1L, 1L)));
        assertEquals(Set.of(1L, 2L), store.ids);
    }

    @Test
    void unreadableSnapshotFailsClosedButLegacyConfigWithoutChannelDoesNotBlock() {
        store.pending.add("ifjenc:v1:broken");
        assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(store.ids.contains(1L));
        store.pending.clear();
        store.configs.add(cipher.encrypt("{\"from\":\"old@example.test\"}"));
        service.delete(1L);
        assertEquals(Set.of(2L), store.ids);
    }

    private static BatchDeleteRequest batch(Long... ids) {
        BatchDeleteRequest request = new BatchDeleteRequest();
        request.setIds(Arrays.asList(ids));
        return request;
    }

    private static NotifyConfigCipher cipher() {
        AppProperties p = new AppProperties();
        p.getSecurity().getAes().setKeyId("delete-test");
        p.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new NotifyConfigCipher(p);
    }

    private static class Store implements MailChannelMapper {
        final Set<Long> ids = new HashSet<>(Set.of(1L, 2L));
        final List<Long> locks = new ArrayList<>();
        final List<String> configs = new ArrayList<>(), pending = new ArrayList<>();

        public MailChannel selectById(Long id) {
            return ids.contains(id) ? new MailChannel() : null;
        }

        public MailChannel selectByIdForUpdate(Long id) {
            locks.add(id);
            return selectById(id);
        }

        public List<MailChannel> selectList(MailChannelPageQuery q) {
            return List.of();
        }

        public int insert(MailChannel row) {
            throw new UnsupportedOperationException();
        }

        public int updateById(MailChannel row) {
            throw new UnsupportedOperationException();
        }

        public List<String> selectEmailConfigurations() {
            return configs;
        }

        public List<String> selectPendingEmailSnapshots() {
            return pending;
        }

        public int deleteByIds(List<Long> selected) {
            int count = ids.size();
            ids.removeAll(selected);
            return count - ids.size();
        }
    }
}
