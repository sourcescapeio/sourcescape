import {
  Controller,
  Get,
  Param,
  Post,
  Put,
} from '@nestjs/common';
import { UsersService } from './users.service';

@Controller()
export class PartyController {
  constructor(
    private readonly usersService: UsersService,
  ) {}

  @Get('/users/:email')
  async getUser(@Param('email') email: string) {
      const user = await this.usersService.getUser(email);

      return {
          user,
      };
  }
}
