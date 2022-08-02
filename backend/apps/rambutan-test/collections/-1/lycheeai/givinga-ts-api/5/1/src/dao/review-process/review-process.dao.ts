import { Injectable } from '@nestjs/common';
import { HoneycombService } from '../../libs/honeycomb/honeycomb.service';
import { Prisma, PrismaClient, ReviewProcess } from '@prisma/client';
import { singleOpt } from '../../libs/db/helpers';
import { requestCallback } from '@grpc/grpc-js';

// TODO: Make sure to add to schema.prisma

@Injectable()
export class ReviewProcessDao {
    constructor(private honeycombService: HoneycombService) {}


    async create(
        prisma: PrismaClient, 
        payload: {
            corporationId: number,
            title: string,
            payload: Object,
            organizationNameKey: string,
            requestedAmountKey: string,
            requestorEmailKey: string | null,
            requestorNameKey: string | null,
            regionKey: string | null,
        }
    ): Promise<ReviewProcess> {
        return singleOpt(prisma.$queryRaw<ReviewProcess[]>`
            INSERT INTO review_process (corporation_id, title, payload, organization_name_key, requested_amount_key, requestor_email_key, requestor_name_key, region_key) VALUES (
            ${payload.corporationId},
            ${payload.title},
            ${payload.payload},
            ${payload.organizationNameKey},
            ${payload.requestedAmountKey},
            ${payload.requestorEmailKey},
            ${payload.requestorNameKey},
            ${payload.regionKey}
            )
            RETURNING *        
        `);
    }

    async listReviewProcess(prisma: PrismaClient, corporationId: number): Promise<ReviewProcess[]> {
        return prisma.$queryRaw<ReviewProcess[]>`
            SELECT review_process.*
            FROM review_process WHERE corporation_id = ${corporationId}
        `;
    }

    async getReviewProcess(prisma: PrismaClient, corporationId: number, processId: number): Promise<ReviewProcess | null> {
        return singleOpt(prisma.$queryRaw<ReviewProcess[]>`
            SELECT review_process.*
            FROM review_process WHERE corporation_id = ${corporationId} AND id = ${processId}
        `);
    }

    async updateReviewProcess(prisma: PrismaClient, corporationId: number, processId: number, organizationNameKey: string | null, requestedAmountKey: string | null, requestorEmailKey: string | null, requestorNameKey: string | null, regionKey: string | null) {

        if (!organizationNameKey && !requestedAmountKey && !requestorEmailKey && !requestorNameKey && !regionKey) {
            return;
        }

        console.warn(requestorEmailKey)

        const organizationNameKeyChange = organizationNameKey ? Prisma.sql`organization_name_key = ${organizationNameKey},` : Prisma.empty;
        const requestedAmountKeyChange = requestedAmountKey ? Prisma.sql`requested_amount_key = ${requestedAmountKey},` : Prisma.empty;
        const requestorEmailKeyChange = requestorEmailKey ? Prisma.sql`requestor_email_key = ${requestorEmailKey},` : Prisma.empty;
        const requestorNameKeyChange = requestorNameKey ? Prisma.sql`requestor_name_key = ${requestorNameKey},` : Prisma.empty;
        const regionKeyChange = regionKey ? Prisma.sql`region_key = ${regionKey},` : Prisma.empty;

        return prisma.$executeRaw`
            UPDATE review_process
            SET 
                ${organizationNameKeyChange}
                ${requestedAmountKeyChange}
                ${requestorEmailKeyChange}
                ${requestorNameKeyChange}
                ${regionKeyChange}
                payload = payload
            WHERE id = ${processId}
        `
    }

    async updateReviewProcessPayload(prisma: PrismaClient, processId: number, payload: any) {
        return prisma.$executeRaw`
            UPDATE review_process SET payload=${payload} WHERE id = ${processId}
        `
    }

    async deleteReviewProcess(prisma: PrismaClient, corporationId: number, processId: number) {
        return prisma.$executeRaw`
        DELETE FROM review_process WHERE corporation_id = ${corporationId} AND id = ${processId}
        `
    }
}
