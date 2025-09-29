package com.joshfouchey.smsarchive.service;

import com.joshfouchey.smsarchive.model.Sms;
import com.joshfouchey.smsarchive.model.Mms;
import com.joshfouchey.smsarchive.repository.SmsRepository;
import com.joshfouchey.smsarchive.repository.MmsRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SearchService {

    private final SmsRepository smsRepo;
    private final MmsRepository mmsRepo;

    public SearchService(SmsRepository smsRepo, MmsRepository mmsRepo) {
        this.smsRepo = smsRepo;
        this.mmsRepo = mmsRepo;
    }

    public List<Sms> searchSmsByAddress(String address) {
        return smsRepo.findByAddress(address);
    }

    public List<Sms> searchSmsByText(String text) {
        return smsRepo.searchByText(text);
    }

    public List<Sms> searchSmsByDateRange(Instant start, Instant end) {
        return smsRepo.findByDateBetween(start, end);
    }

    public List<Mms> searchMmsByAddress(String address) {
        return mmsRepo.findByAddress(address);
    }

    public List<Mms> searchMmsByText(String text) {
        return mmsRepo.searchByPartText(text);
    }

    public List<Mms> searchMmsByDateRange(Instant start, Instant end) {
        return mmsRepo.findByDateBetween(start, end);
    }
}
