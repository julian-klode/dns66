/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */

package org.jak_linux.dns66.vpn;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A FileInputStream that can be interrupted.
 */
public class InterruptibleFileInputStream extends FileInputStream {

    private FileDescriptor mInterruptFd = null;
    private FileDescriptor mBlockFd = null;

    public InterruptibleFileInputStream(FileDescriptor fd) throws IOException, ErrnoException {
        super(fd);

        FileDescriptor[] pipes = Os.pipe();
        mInterruptFd = pipes[0];
        mBlockFd = pipes[1];
    }

    private BlockInterrupt blockRead() throws ErrnoException, IOException {
        StructPollfd mainFd = new StructPollfd();
        mainFd.fd = getFD();
        mainFd.events = (short) OsConstants.POLLIN;
        StructPollfd blockFd = new StructPollfd();
        blockFd.fd = mBlockFd;
        blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

        StructPollfd[] pollArray = {mainFd, blockFd};

        while (true) {
            try {
                Os.poll(pollArray, -1);
                break;
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.EINTR)
                    throw e;
            }
        }


        if (pollArray[1].revents != 0) {
            return BlockInterrupt.INTERRUPT;
        } else {
            return BlockInterrupt.OK;
        }
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        try {
            if (blockRead() == BlockInterrupt.INTERRUPT) {
                throw new InterruptedStreamException();
            }
        } catch (ErrnoException e) {
            throw new IOException(e);
        }

        return super.read(buffer);
    }

    public void interrupt() throws IOException {
        if (mInterruptFd != null) {
            try {
                Os.close(mInterruptFd);
            } catch (ErrnoException e) {
                throw new IOException(e);
            }
            mInterruptFd = null;
        }
    }

    private enum BlockInterrupt {
        OK, INTERRUPT
    }

    public class InterruptedStreamException extends RuntimeException {
    }
}