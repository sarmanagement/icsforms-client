#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

volume="$PWD/platform-volume"
image="$PWD/platform-volume.img"
case_name="${PLATFORM_FILESYSTEM_CASE:?}"

if [[ "${1:-mount}" == cleanup ]]; then
    if [[ "$case_name" == macos-exfat ]]; then
        if [[ -f platform-volume.device ]]; then
            hdiutil detach "$(cat platform-volume.device)" -force || true
        fi
    elif [[ "$case_name" == linux-* ]]; then
        if mountpoint -q "$volume"; then
            sudo umount "$volume"
        fi
        if [[ "$case_name" == linux-nfs ]] && [[ -f /etc/exports.d/icsforms-platform.exports ]]; then
            sudo rm /etc/exports.d/icsforms-platform.exports
            sudo exportfs -ra
        fi
    fi
    exit 0
fi

mkdir -p "$volume"
case "$case_name" in
    linux-fat-default|linux-fat-owner)
        truncate -s 128M "$image"
        mkfs.vfat -F 32 "$image"
        options="loop,uid=$(id -u),gid=$(id -g)"
        if [[ "$case_name" == linux-fat-owner ]]; then
            # Linux vfat rejects gmask; dmask/fmask provide the restrictive-mask equivalent.
            options="$options,dmask=077,fmask=077"
            echo "vfat uses dmask/fmask instead of the unsupported gmask option"
        fi
        sudo mount -t vfat -o "$options" "$image" "$volume"
        ;;
    linux-ntfs)
        truncate -s 256M "$image"
        mkfs.ntfs -F -Q "$image"
        sudo mount -t ntfs-3g -o "loop,uid=$(id -u),gid=$(id -g),permissions" "$image" "$volume"
        probe="$volume/permission-probe"
        touch "$probe"
        chmod 600 "$probe"
        stat -c 'ntfs-3g probe mode=%a owner=%U' "$probe"
        rm "$probe"
        ;;
    linux-nfs)
        sudo mkdir -p /etc/exports.d
        mkdir -p platform-export
        chmod 777 platform-export
        printf '%s 127.0.0.1(rw,sync,no_subtree_check,no_root_squash)\n' "$PWD/platform-export" |
            sudo tee /etc/exports.d/icsforms-platform.exports
        sudo systemctl start rpcbind nfs-kernel-server
        sudo exportfs -ra
        sudo mount -t nfs -o vers=4 127.0.0.1:"$PWD/platform-export" "$volume"
        ;;
    macos-exfat)
        # hdiutil create is refused on hosted runners, so format a RAM disk instead.
        device="$(hdiutil attach -nomount ram://262144 | awk '{print $1}')"
        echo "$device" > platform-volume.device
        diskutil eraseVolume ExFAT ICSPLATFORM "$device"
        volume=/Volumes/ICSPLATFORM
        ;;
    macos-apfs)
        volume="$PWD"
        ;;
    *)
        echo "Unknown case: $case_name" >&2
        exit 1
        ;;
esac
printf 'PLATFORM_FILESYSTEM_ROOT=%s\n' "$volume" >> "$GITHUB_ENV"
mount | grep -F "$volume" || true
